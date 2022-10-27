package com.hmdp;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
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
    @org.junit.jupiter.api.Test
    public void test(){
        JSONArray objects = JSONUtil.parseArray("[{\"icon\":\"/types/ms.png\",\"updateTime\":1640229871000,\"sort\":1,\"createTime\":1640175467000,\"name\":\"美食\",\"id\":1}]");
        System.out.println(objects);
        List<ShopType> shopTypes = JSONUtil.toList(objects, ShopType.class);
        System.out.println(shopTypes);
    }
}
