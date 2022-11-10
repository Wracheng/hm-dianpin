---
--- Generated by EmmyLua(https://github.com/EmmyLua)
--- Created by Mucheng.
--- DateTime: 2022/11/8 16:01
---

local couponId = ARGV[1];
local userId = ARGV[2];
local orderId = ARGV[3];
-- ..是拼接字符串的意思
local stockKey = "seckill:stock:" .. couponId
local orderKey = "seckill:order:" .. couponId

-- 判断库存是否充足
if(tonumber(redis.call('get',stockKey) <= 0)) then
    return 1;
end

--判断用户是否已经买过（不写==是因为可能这张券以前不是一人一单券）
if(redis.call('sismember',orderKey,userId) >= 1) then
    return 2;
end

-- redis扣库存
redis.call('incrby',stockKey,-1);

-- redis下单
redis.call('sadd',orderKey,userId);

-- 添加消息到队列中
redis.call('xadd','stream.orders','*','userId',userId,'voucherId',couponId,'id',orderId);
return 0;