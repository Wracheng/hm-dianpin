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
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import com.baomidou.mybatisplus.extension.service.impl.*;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

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
    private ServiceImpl<ShopMapper, Shop> s;

    @org.junit.jupiter.api.Test
    public void test() {
        System.out.println(StrUtil.isNotBlank(null));
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
}
