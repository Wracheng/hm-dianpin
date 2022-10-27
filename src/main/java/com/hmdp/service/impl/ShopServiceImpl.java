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
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

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
            stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + "id", shopStr,RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
            return Result.ok(shopInfo);
        }

    }

    @Override
    // 加上事务处理，这个方法一旦出错，mysql就会触发回滚机制
    @Transactional
    public Result updateShop(Shop shop) {
        Long id = shop.getId();
        if(id == null){
            return Result.fail("店铺id不能为空");
        }
        // 更新数据库，由于mysql有事务的原子性，所以操作有异常能回滚
        updateById(shop);

        // 删除redis中当前店铺的信息，redis的事务只是一串命令的集合要么执行要么不执行，没有回滚的功能，出现问题需要手动解决
        stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY + id);

        return Result.ok("操作成功");
    }
}
