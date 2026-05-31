# 黑马点评实战篇笔记



### 获取验证码

```
1. 校验手机号是否合法
2. 生成验证码
3. 保存验证码到session
4. 发送验证码
```

### 登录

```
1. 校验手机号
2. 校验验证码是否与session中的一致
3. 查询该手机号关联的用户是否存在，不存在则注册用户信息，并保存到数据库
4. 保存用户信息到session 当中	
```

### 获取当前登录的用户信息并返回

```
增加登录拦截器，因为后续很多地方都需要使用到用户信息，都需要用户登录了，才能使用这些功能
每次登录时，先从 session 中取到用户信息
如果没有则拦截
用则保存到 ThreadLocal 当中，方便后续使用
返回用户信息
```



### 登录用户信息脱敏

```
保存到 session 当中的用户信息需要脱敏
```



### 集群 session 共享问题

```
使用 redis 代替 session 存储
```



### Redis 解决

```
1. 验证码存储 redis, 使用 手机号作为 key，同时设置过期时间
2. 登录时候，生成token, 使用token 作为储位用户信息 的redis key，同时也要设置过期时间，并把token 返回给 前端 保存到 auth
3. 修改 登录拦截器，从 请求头中获取token ,再以 token 作为 key 查询 redis 当中的 用户信息
```



### 解决 token 刷新问题

```
1. 再增加一个拦截器，只负责刷新
2. 修改登录拦截器，只判断 ThreadLocal 中是否有值
```



### 商铺根据 id 查询接口增加缓存

```
1. 先查 redis 有 返回 没有 查数据库 ，写 redis 返回
```



### 店铺类型增加查询缓存



### 缓存更新策略

![image-20260413195648895](https://raw.githubusercontent.com/PeterFangXiaoLin/imgs/main/test/20260530111131554.png)

![image-20260413200008213](https://raw.githubusercontent.com/PeterFangXiaoLin/imgs/main/test/20260530111149871.png)

![image-20260413200248523](https://raw.githubusercontent.com/PeterFangXiaoLin/imgs/main/test/20260530111218717.png)

![image-20260413201707243](https://raw.githubusercontent.com/PeterFangXiaoLin/imgs/main/test/20260530111236008.png)

```
1. 靠redis 内存不足淘汰
2. 设置 key的过期时间
3. 主动更新缓存

1. cache aside 
主动更新缓存
```



### 根据上面的策略修改商铺查询缓存

```
1. 查询接口增加缓存过期时间
2. 修改接口先更新数据库，再删除缓存
```



### 缓存穿透

```
查询缓存没有，查询数据库也没有的数据
解决：
1. 缓存null值
2. 布隆过滤器
```

![image-20260413202758842](https://raw.githubusercontent.com/PeterFangXiaoLin/imgs/main/test/20260530111250039.png)



### 缓存雪崩

```
大量key同时过期
```

![image-20260413203836065](https://raw.githubusercontent.com/PeterFangXiaoLin/imgs/main/test/20260530111305369.png)



### 缓存击穿

![image-20260413204444006](https://raw.githubusercontent.com/PeterFangXiaoLin/imgs/main/test/20260530111319097.png)

![image-20260413205849780](https://raw.githubusercontent.com/PeterFangXiaoLin/imgs/main/test/20260530111332347.png)



```
互斥锁实现
1. 写一个获取锁和一个释放锁的方法
```





### 封装 Redis 工具

![image-20260425151338957](https://raw.githubusercontent.com/PeterFangXiaoLin/imgs/main/test/20260530111344212.png)



### Redis 全局ID生成器

![image-20260426155502673](https://raw.githubusercontent.com/PeterFangXiaoLin/imgs/main/test/20260530111356035.png)





## 秒杀优惠券

### 优惠券下单

![image-20260501161056017](https://raw.githubusercontent.com/PeterFangXiaoLin/imgs/main/test/20260530111407591.png)

```
1. 校验参数
	优惠券是否存在
	是否在秒杀时间内
2. 查询库存是否充足
3. 扣减库存
4. 创建订单
5. 返回订单id
```

### 超卖问题

![image-20260501164352706](https://raw.githubusercontent.com/PeterFangXiaoLin/imgs/main/test/20260530111419519.png)



增加版本号和 CAS

![image-20260501164509827](https://raw.githubusercontent.com/PeterFangXiaoLin/imgs/main/test/20260530111430649.png)

![image-20260501165620878](https://raw.githubusercontent.com/PeterFangXiaoLin/imgs/main/test/20260530111447954.png)





### 一人一单

加锁实现

```
先查订单表的userId和vouncherId 是否存在
```



### 集群模式下处理

![image-20260501232724665](https://raw.githubusercontent.com/PeterFangXiaoLin/imgs/main/test/20260530111505695.png)

#### 基于 Redis 实现

![image-20260501233248222](https://raw.githubusercontent.com/PeterFangXiaoLin/imgs/main/test/20260530111515069.png)



由于锁过期会自动释放，如果在处理业务逻辑出现阻塞，锁释放了，这是其他线程进来加锁，

然后线程1，执行释放锁，释放了别人的锁，

所以需要加一个释放锁的判断



同时，释放锁，这一段需要封装成一个原子操作，要么全部成功，要么全部失败。

![image-20260502073014783](https://raw.githubusercontent.com/PeterFangXiaoLin/imgs/main/test/20260530111527332.png)

![image-20260502081254982](https://raw.githubusercontent.com/PeterFangXiaoLin/imgs/main/test/20260530111539400.png)



基于 Redis 实现的分布式锁，并不完善，解决上面的问题，需要引入 Redisson 

修改 为使用 Redisson 版本

![image-20260502170429557](https://raw.githubusercontent.com/PeterFangXiaoLin/imgs/main/test/20260530111553947.png)





主从一致解决

![image-20260502174700775](https://raw.githubusercontent.com/PeterFangXiaoLin/imgs/main/test/20260530111608251.png)







### 秒杀优化

![image-20260502185806238](https://raw.githubusercontent.com/PeterFangXiaoLin/imgs/main/test/20260530111619387.png)



```
1. 新增秒杀券时，保存库存到Redis中
2. 基于 lua 脚本，判断库存，一人一单，决定用户是否抢购成功
3. 如果抢购成功，将优惠券id和用户id封装成订单存入阻塞队列
4. 开启线程任务，不断从阻塞队列中获取信息，实现异步下单功能
```



### 使用阻塞队列优化

ArrayBlockingQueue

### 使用 Redis  stream 数据结构当简单的消息队列

![image-20260517151609573](https://raw.githubusercontent.com/PeterFangXiaoLin/imgs/main/test/20260530111632139.png)

### 发布探店笔记

修改图片的存储地址即可



### 查看探店笔记

根据id 查询 blog，关联查询出相关的用户信息返回



### 点赞实现

![image-20260528213219198](https://raw.githubusercontent.com/PeterFangXiaoLin/imgs/main/test/20260530111642795.png)

### 点赞排行榜

查询点赞的前五名用户

需要排序，原有点赞列表使用 set 存储，默认无序，无法满足

改用 sortedSet，时间戳作为 score 可以实现点赞排行

1. 未登录用户获取点赞详情会报错，需要修改
2. sql in 查询不会根据 in 填入的 内容顺序进行返回，需要加一个排序

### 关注和取关

保存数据到中间表

### 共同关注

关注后，额外保存一份数据到 set 集合中，两个集合求交集

### Feed流推送

![image-20260530163428666](https://raw.githubusercontent.com/PeterFangXiaoLin/imgs/main/test/20260530163432488.png)

![image-20260530163631094](https://raw.githubusercontent.com/PeterFangXiaoLin/imgs/main/test/20260530163632980.png)

![image-20260530163756513](https://raw.githubusercontent.com/PeterFangXiaoLin/imgs/main/test/20260530163758550.png)

![image-20260530164032126](https://raw.githubusercontent.com/PeterFangXiaoLin/imgs/main/test/20260530164033676.png)

![image-20260530164206970](https://raw.githubusercontent.com/PeterFangXiaoLin/imgs/main/test/20260530164208622.png)

### 用推模式实现关注推送

![image-20260530164534176](https://raw.githubusercontent.com/PeterFangXiaoLin/imgs/main/test/20260530164537588.png)

![image-20260530164700179](https://raw.githubusercontent.com/PeterFangXiaoLin/imgs/main/test/20260530164701480.png)

![image-20260530164918231](https://raw.githubusercontent.com/PeterFangXiaoLin/imgs/main/test/20260530164919441.png)

1. 添加笔记时，查询该用户的粉丝，向给用户的每个粉丝的推送箱发送笔记id
2. 查询时，获取当前登录用户的id，获取该用户的推送箱，根据上一次的时间戳，以及偏移量，求本次需要查询的笔记，如果是第一次，偏移量为0，时间戳为当前时间戳。



### 附近商铺搜索

![image-20260531202213586](https://raw.githubusercontent.com/PeterFangXiaoLin/imgs/main/test/20260531202222999.png)

### 用户签到

![image-20260531220705590](https://raw.githubusercontent.com/PeterFangXiaoLin/imgs/main/test/20260531220708179.png)

![image-20260531221302889](https://raw.githubusercontent.com/PeterFangXiaoLin/imgs/main/test/20260531221304290.png)

### 签到统计

![image-20260531223123062](https://raw.githubusercontent.com/PeterFangXiaoLin/imgs/main/test/20260531223125235.png)

### UV统计

![image-20260531232851001](https://raw.githubusercontent.com/PeterFangXiaoLin/imgs/main/test/20260531232852537.png)

![image-20260531233036053](https://raw.githubusercontent.com/PeterFangXiaoLin/imgs/main/test/20260531233037315.png)

