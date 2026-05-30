-- 1. 参数列表
-- 1.1 优惠券id
local voucherId = ARGV[1]

-- 1.2 用户id
local userId = ARGV[2]

-- 1.3 订单 id
local orderId = ARGV[3]

-- 2. 定义需要的key
-- 库存key
local stockKey = 'seckill:stock:' .. voucherId
-- 库存下单的用户id集合key
local orderKey = 'seckill:order:' .. voucherId

-- 3. 判断库存是否充足
if (tonumber(redis.call('get', stockKey)) <= 0) then
    -- 库存不足
    return 1
end

-- 4. 判断是否下过单（一人一单）
if (redis.call('sismember', orderKey, userId) == 1) then
    -- 下过单，
    return 2
end

-- 5. 满足，
-- 扣减库存
redis.call('incrby', stockKey, -1)
-- 加入 用户id 到 set
redis.call('sadd', orderKey, userId)
-- 发送消息到队列中， XADD stream.orders * k1 v1 k2 v2 k3 v3
redis.call('xadd', 'stream.orders', "*", 'userId', userId, 'voucherId', voucherId, 'id', orderId)
return 0