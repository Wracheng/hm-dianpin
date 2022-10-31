package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ReactiveRedisOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.sql.Time;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * @author Mr.Wang
 * @version 1.0
 * @since 1.8
 */
@Slf4j
@Component
public class RedisCacheUtil {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    private static final ExecutorService CACHE_REBUILD_EXRCUTOR = Executors.newFixedThreadPool(10);

    public void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    // 设置带有逻辑过期字段的redis
    public void setWithExpire(String key, Object value, Long time, TimeUnit unit) {
        RedisData redisData = new RedisData();
        redisData.setData(value);
        // 把各种时间单位时分秒都以秒的形式存在
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    /**
     * 解决缓存穿透
     * shopinfo只是历史遗留字段，可以看做通用的info
     *
     * @param prefix key的前缀
     *               callback 回调函数，注意Function<...参数类型,最后一个是返回值类型>
     * @return 查询的结果
     */
    public <T, ID> T queryHandleCT(ID id, String prefix, Class<T> type, Function<ID, T> callback, Long time, TimeUnit unit) {
        String key = prefix + id;
        // TODO  去redis中查看能不能查到
        String info = stringRedisTemplate.opsForValue().get(prefix + id);
        // 判断该类型是有值的字符串
        if (StrUtil.isNotBlank(info)) {
            // json转bean
            // 实参里面不能有泛型，要用形参代替
            return JSONUtil.toBean(info, type);
        } else {
            // 重点 如果从redis取出来是""，说明是为了防止缓存穿透加的值，店铺是不存在的
            if (Objects.equals(info, "")) {
                return null;
            }
            // TODO redis中查不到去数据库查，由于你不知道是根据什么来查，所以交给调用者用回调的方式
            T shopInfo = callback.apply(id);

            if (shopInfo == null) {
                // 重点 为了防止缓存穿透，在数据库查不到的时候在缓存加一个值
                set(key, "", time, unit);
                return null;
            }
            // TODO 数据库中查到了就存入redis，查不到就返回该商品不存在
            String shopStr = JSONUtil.toJsonStr(shopInfo);
            set(key, shopStr, time, unit);
            return shopInfo;
        }
    }

    public <T, ID> T queryShopInfoHandleJC2(ID id, String prefix, Class<T> type, Function<ID, T> callback, Long time, TimeUnit unit) {
        String key = prefix + id;
        // TODO  去redis中查看能不能查到
        String info = stringRedisTemplate.opsForValue().get(key);
        // 判断该类型是不是`有值的字符串`
        if (StrUtil.isBlank(info)) {
            return null;
        }

        // 查看key有没有逻辑过期
        RedisData redisData = JSONUtil.toBean(info, RedisData.class);
        // 只返回shop数据，不返回redis的逻辑过期时间
        T shop = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        if (redisData.getExpireTime().isAfter(LocalDateTime.now())) {
            return shop;
        }

        // 过期了,重新查数据库写入redis
        // - 获取互斥锁
        boolean flag = tryGetLock(key);
        if (!flag) {
            // 获取锁失败返回旧数据
            return shop;
        }
        try {
            // 重点 再次查找redis有没有过期（可能在这个线程判断过期之后被其他线程抢走之后又重新写入redis了）
            String info2 = stringRedisTemplate.opsForValue().get(key);
            RedisData redisData2 = JSONUtil.toBean(info2, RedisData.class);
            T shop2 = JSONUtil.toBean((JSONObject) redisData2.getData(), type);
            if (redisData.getExpireTime().isAfter(LocalDateTime.now())) {
                return shop2;
            }
            // 使用线程池开启一个线程去做这个更新redis操作
            CACHE_REBUILD_EXRCUTOR.submit(() -> {
                // 查询数据库
                T shopInfo = callback.apply(id);
                setWithExpire(key, shopInfo, time, unit);
                return Result.ok(shopInfo);
            });
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            deleteLock(key);
        }
        // 开启线程处理自己就返回旧数据
        return shop;
    }

    // 获取互斥锁
    public boolean tryGetLock(String key) {
        // 采用setnx 没有就操作的方法来充当锁
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
        return BooleanUtil.isTrue(flag);
    }

    // 释放锁
    public void deleteLock(String key) {
        stringRedisTemplate.delete(key);
    }

}
