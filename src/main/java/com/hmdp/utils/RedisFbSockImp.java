package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.apache.ibatis.annotations.Insert;
import org.redisson.Redisson;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * @author Mr.Wang
 * @version 1.0
 * @since 1.8
 */
@Component
public class RedisFbSockImp implements RedisFbSock {
    // 键名，包含业务名，用户id（如果想要再精确一点再加个优惠券id），做到一人一锁
    private String name;
    private static final String PREFIX = "lock:";
    private static final String PREFIX_UUID = UUID.randomUUID().toString(true) + "-";
    // static 提前定义好，就不用每次释放锁都来创建
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RedissonClient redissonClient;
    static {
        UNLOCK_SCRIPT = new  DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }
    // 需要加上空参构造，不然无法通过@Component交给IOC管理
    public RedisFbSockImp() {
    }

    public RedisFbSockImp(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean tryGetLock(long timeout) {
        // 1、判断锁存不存在 （这一段是原子操作）
        Boolean isGetLock = stringRedisTemplate.opsForValue().setIfAbsent(PREFIX + name, PREFIX_UUID + Thread.currentThread().getId() + "", timeout, TimeUnit.SECONDS);
        // 避免isGetLock为null产生拆箱空指针异常
        return Boolean.TRUE.equals(isGetLock);
    }

    @Override
    public void unLock() {
        /*// 判断是不是自己的锁，不是不处理
        String value = stringRedisTemplate.opsForValue().get(PREFIX + name);
        String currentValue = PREFIX_UUID + Thread.currentThread().getId();
        if (currentValue.equals(value)) {
            stringRedisTemplate.delete(PREFIX + name);
        }*/
        // 使用lua脚本执行保证判断和释放两个操作的原子性
        stringRedisTemplate.execute(UNLOCK_SCRIPT,Collections.singletonList(PREFIX + name),PREFIX_UUID + Thread.currentThread().getId());

    }
}
