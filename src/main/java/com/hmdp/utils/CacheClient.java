package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class CacheClient {

    private final StringRedisTemplate stringRedisTemplate;

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);


    public void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSON.toJSONString(value), time, unit);
    }

    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        stringRedisTemplate.opsForValue().set(key, JSON.toJSONString(redisData));
    }

    public <R, ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        // 1. 先查 redis
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        // 2. 存在直接返回
        if (StrUtil.isNotBlank(json)) {
            return JSON.parseObject(json, type);
        }

        // 判断是否是空值
        if (json != null) {
            return null;
        }

        // 3. 不存在 查数据库
        R r = dbFallback.apply(id);
        if (r == null) {
            // 缓存空值(时间要比正常数据时间短)
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }

        // 4. 写入 redis
        this.set(key, JSON.toJSONString(r), time, unit);
        return r;
    }

    public <R, ID> R queryWithLogicalExpire(String keyPrefix, ID id, Class<R> type, String lockKeyPrefix, Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        // 先查询缓存
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isBlank(json)) {
            // 缓存不存在，返回null
            return null;
        }

        // 缓存存在
        // 校验是否过期
        RedisData redisData = JSON.parseObject(json, RedisData.class);
        R r = JSON.parseObject(JSON.toJSONString(redisData.getData()), type);
        LocalDateTime expireTime = redisData.getExpireTime();
        if (expireTime.isAfter(LocalDateTime.now())) {
            // 缓存未过期
            return r;
        }

        // 缓存已过期，枪锁更新缓存
        String lockKey = lockKeyPrefix + id;
        if (tryLock(lockKey)) {
            // 抢到锁
            // 开启新线程重建缓存，返回旧数据
            // 再次查缓存，双重检查：防止并发场景下重复重建
            String newJson = stringRedisTemplate.opsForValue().get(key);
            if (StrUtil.isNotBlank(newJson)) {
                RedisData newRedisData = JSON.parseObject(newJson, RedisData.class);
                // 逻辑过期：必须再次校验过期时间
                if (newRedisData.getExpireTime().isAfter(LocalDateTime.now())) {
                    // 其他线程已重建完成，返回新数据
                    unlock(lockKey);
                    return JSON.parseObject(JSON.toJSONString(redisData.getData()), type);
                }
            }

            // 开启新线程更新缓存
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    R r1 = dbFallback.apply(id);
                    this.setWithLogicalExpire(key, r1, time, unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    unlock(lockKey);
                }
            });
            // 当前线程返回旧数据
            return r;
        }
        // 没抢到锁，返回旧数据
        return r;
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

}
