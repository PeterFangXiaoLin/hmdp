package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisData;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@RequiredArgsConstructor
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    private final StringRedisTemplate stringRedisTemplate;
    private final RedisTemplate<Object, Object> redisTemplate;
    private final CacheClient cacheClient;
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    @Override
    public Result queryById(Long id) {
        // 缓存击穿写法（缓存空值）
//        Shop shop = queryWithPassThrough(id);
        // 工具调用
//        Shop shop = cacheClient.queryWithPassThrough(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);



        // 缓存击穿（互斥锁）
//        Shop shop = queryWithMutex(id);

        // 缓存击穿（逻辑过期）
//        Shop shop = queryWithLogicalExpire(id);

        // 缓存击穿，逻辑过期，工具调用
        Shop shop = cacheClient.queryWithLogicalExpire(CACHE_SHOP_KEY, id, Shop.class, LOCK_SHOP_KEY, this::getById, 20L, TimeUnit.SECONDS);

        if (shop == null) {
            return Result.fail("商铺不存在！");
        }
        return Result.ok(shop);
    }

    /**
     * 缓存击穿（互斥锁）
     *
     * @param id id
     * @return shop
     */
    private Shop queryWithMutex(Long id) {
        // 1. 先查redis
        String key = CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        // 2. 存在直接返回
        if (StrUtil.isNotBlank(shopJson)) {
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        // 3. 不存在，判断是否为空值
        if (shopJson != null) {
            return null;
        }

        Shop shop = null;
        // 4. 不存在，申请锁
        String lockKey = LOCK_SHOP_KEY + id;
        try {
            if (!tryLock(lockKey)) {
                // 睡一会，递归
                Thread.sleep(50);
                return queryWithMutex(id);
            }

            // 5. 拿到锁，

            // 再次查询缓存，校验缓存是否存在
            shopJson = stringRedisTemplate.opsForValue().get(key);
            if (StrUtil.isNotBlank(shopJson)) {
                return JSONUtil.toBean(shopJson, Shop.class);
            }
            if (shopJson != null) {
                return null;
            }

            // 查数据库
            shop = getById(id);
            if (shop == null) {
                // 数据库不存在，缓存空值
                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            // 不是空，存储缓存
            long ttlWithRandom = CACHE_SHOP_TTL + RandomUtil.randomLong(0, 5);
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), ttlWithRandom, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            // 释放锁
            unlock(lockKey);
        }

        return shop;
    }

    /**
     * 缓存击穿（逻辑过期）
     *
     * @param id shop id
     * @return 商铺信息
     */
    private Shop queryWithLogicalExpire(Long id) {
        // 先查询缓存
        String key = CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isBlank(shopJson)) {
            // 缓存不存在，返回null
            return null;
        }

        // 缓存存在
        // 校验是否过期
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        if (expireTime.isAfter(LocalDateTime.now())) {
            // 缓存未过期
            return shop;
        }

        // 缓存已过期，枪锁更新缓存
        String lockKey = LOCK_SHOP_KEY + id;
        if (tryLock(lockKey)) {
            // 抢到锁
            // 开启新线程重建缓存，返回旧数据
            // 再次查缓存，双重检查：防止并发场景下重复重建
            String newShopJson = stringRedisTemplate.opsForValue().get(key);
            if (StrUtil.isNotBlank(newShopJson)) {
                RedisData newRedisData = JSONUtil.toBean(newShopJson, RedisData.class);
                // 逻辑过期：必须再次校验过期时间
                if (newRedisData.getExpireTime().isAfter(LocalDateTime.now())) {
                    // 其他线程已重建完成，返回新数据
                    unlock(lockKey);
                    return JSONUtil.toBean((JSONObject) newRedisData.getData(), Shop.class);
                }
            }

            // 开启新线程更新缓存
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    this.saveShop2Redis(id, 20L);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    unlock(lockKey);
                }
            });
            // 当前线程返回旧数据
            return shop;
        }
        // 没抢到锁，返回旧数据
        return shop;
    }

    /**
     * 申请互斥锁
     *
     * @param key key
     * @return 是否申请成功
     */
    public boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    /**
     * 释放互斥锁
     *
     * @param key key
     */
    public void unlock(String key) {
        stringRedisTemplate.delete(key);
    }

    /**
     * 根据 shop id 构建缓存
     *
     * @param id         shop id
     * @param expireSeconds 过期时间
     */
    public void saveShop2Redis(Long id, Long expireSeconds) {
        // 查询数据库
        Shop shop = getById(id);
        // 封装数据
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        // 写入redis(逻辑过期，不设置过期时间)
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }

    /**
     * 缓存穿透写法（缓存空值）
     * @param id id
     * @return shop
     */
    private Shop queryWithPassThrough(Long id) {
        // 1. 先查 redis
        String key = CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        // 2. 存在直接返回
        if (StrUtil.isNotBlank(shopJson)) {
            return JSONUtil.toBean(shopJson, Shop.class);
        }

        // 判断是否是空值
        if (shopJson != null) {
            return null;
        }

        // 3. 不存在 查数据库
        Shop shop = getById(id);
        if (shop == null) {
            // 缓存空值(时间要比正常数据时间短)
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }

        // 4. 写入 redis
        // 增加过期时间（添加随机偏移量，防止缓存雪崩）
        long ttlWithRandom = CACHE_SHOP_TTL + RandomUtil.randomLong(0, 5);
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), ttlWithRandom, TimeUnit.MINUTES);
        return shop;
    }

    @Override
    @Transactional // 事务保证一致性
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("商铺id不能为空");
        }

        // 先更新数据库
        updateById(shop);
        // 再删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
        return Result.ok();
    }
}
