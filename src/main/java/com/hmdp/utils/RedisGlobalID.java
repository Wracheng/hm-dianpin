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
    private final int COUNT_BITS = 32;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    // 获取下一个Id
    public long nextId(String prefix){
        // 获取时间戳
        LocalDateTime now = LocalDateTime.now();
        long nowStampTime = now.toEpochSecond(ZoneOffset.UTC);
        long stamp = nowStampTime - BEGIN_STAMPTIME;
        // 获取序列号
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        // 以增量方式存进redis，常用来做统计
        Long count = stringRedisTemplate.opsForValue().increment("icr:" + prefix + date);
        // 重点 拼接返回，数值类型的拼接需要移位，由于BEGIN_STAMPTIME是long本身占64位但是一般只需要用到后面的32位就行了（而且第一位必须是0，左移32位要作为首位符号位的,31位秒数最多可以表达68年）
        // 重点 count虽然也是long，也一般只要用到后面的32位，,前32位都是0，由于key是分店铺和每日的，所以2^32次方最多能表示42.9亿，足够用了
        return stamp << COUNT_BITS | count;
    }
}
