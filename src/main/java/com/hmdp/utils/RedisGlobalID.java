package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

/**
 * @author Mr.Wang
 * @version 1.0
 * @since 1.8
 */
@Component
public class RedisGlobalID {
    // 开始时间戳，这里用2022年1月1日0点举个例子
    private final long BEGIN_STAMPTIME = 1640995200L;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    public void getId(String prefix){
        // 获取时间戳
        LocalDateTime now = LocalDateTime.now();
        long nowStampTime = now.toEpochSecond(ZoneOffset.UTC);
        long l = nowStampTime - BEGIN_STAMPTIME;
        // 获取序列号
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        stringRedisTemplate.opsForValue().increment("icr:" + prefix + date);
        // 重点 拼接返回，数值类型的拼接需要移位
    }
}
