-- 锁的key
local key = KEY[1];
--当前线程提示
local threadId = ARGV[1]
--获取锁中的线程提示
local id = redis.call('get',KEY[1])
-- 比较线程标识与锁的标识是否一致
if(id == threadId) then
    -- 释放锁
    return redis.call('del',key)
end
return 0
