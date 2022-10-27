package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSON;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 木城
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryType() {
        // 从redis里面获取商铺类型
        List<String> shopTypeList = stringRedisTemplate.opsForList().range(RedisConstants.CACHE_SHOP_KEY, 0, -1);
        assert shopTypeList != null;
        if(shopTypeList.size() == 0){
           // 去数据库里查
            List<ShopType> list = list();
            if(list == null){
                return Result.fail("暂无商铺类型");
            }
            // 将数据保存在redis
            for(ShopType item : list){
                String JsonItem = JSONUtil.toJsonStr(item);
                stringRedisTemplate.opsForList().leftPush(RedisConstants.CACHE_SHOP_KEY,JsonItem);
            }
            return Result.ok(list);
        }else{
            String str = JSONUtil.toJsonStr(shopTypeList);
            List<ShopType> list = JSONUtil.toList(str, ShopType.class);
            return Result.ok(list);
        }

    }
}
