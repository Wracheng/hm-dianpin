package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result queryShopInfo(Long id) {
        // TODO  去redis中查看能不能查到
        String info = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY + "id");
        // 判断该类型是有值的字符串
        if(StrUtil.isNotBlank(info)){
            // json转bean
            Shop shop = JSONUtil.toBean(info, Shop.class);
            return Result.ok(shop);

        }else{
            // TODO redis中查不到去数据库查
            Shop shopInfo = getById(id);
            if(shopInfo == null){
                return Result.fail("店铺不存在");
            }
            // TODO 数据库中查到了就存入redis，查不到就返回该商品不存在
            String shopStr = JSONUtil.toJsonStr(shopInfo);
            stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + "id", shopStr);
            return Result.ok(shopInfo);
        }

    }
}
