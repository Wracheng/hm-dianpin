package com.hmdp;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import com.hmdp.utils.RedisGlobalID;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import com.baomidou.mybatisplus.extension.service.impl.*;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;

/**
 * @author Mr.Wang
 * @version 1.0
 * @since 1.8
 */

@Slf4j
@SpringBootTest
public class Test {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedisGlobalID redisGlobalID;

    private ExecutorService executorService = Executors.newFixedThreadPool(500);

    @Resource
    private ServiceImpl<ShopMapper, Shop> s;

    @org.junit.jupiter.api.Test
    public void test() {
        LocalDateTime of = LocalDateTime.of(2022, 1, 1, 0, 0, 0);
        System.out.println(of.toEpochSecond(ZoneOffset.UTC));
    }

    // junit的方法必须public void
    @org.junit.jupiter.api.Test
    public void addExpireRedis() {
        Shop shopInfo = s.getById(1L);
        RedisData redisData = new RedisData();
        redisData.setData(shopInfo);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(108000));
        String redisDataStr = JSONUtil.toJsonStr(redisData);
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + 1, redisDataStr);
        System.out.println(shopInfo);
    }
    @org.junit.jupiter.api.Test
    public void globalUniqueRedisId() throws InterruptedException {
        // 用来让线程执行完毕主线程再执行，300是指有300个线程，因为CountDownLatch的原理是执行完一个线程，线程数会减一，等减到0，主线程就可以执行了，类似async、await
        CountDownLatch countDownLatch = new CountDownLatch(300);
        Runnable task = () -> {
            for(int i = 0; i < 100; i++){
                long shop1 = redisGlobalID.nextId("shop1");
                System.out.println(shop1);
            }
            // 一个线程执行完让线程数减一
            countDownLatch.countDown();
        };
        long start = System.currentTimeMillis();
        System.out.println("start=" + start);
        for(int i = 0; i < 300; i++){
            executorService.submit(task);
        }
        // 线程执行完毕
        countDownLatch.await();
        long end = System.currentTimeMillis();
        System.out.println("end=" + end);
        System.out.println("消耗的时间=" + (end - start));
        // 执行完上述操作后，redis中多了一个 key: icr:shop12022:11:01，value： 60000
    }
}
