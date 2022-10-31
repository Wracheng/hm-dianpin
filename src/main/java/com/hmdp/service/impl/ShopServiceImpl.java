package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private static final ExecutorService CACHE_REBUILD_EXRCUTOR = Executors.newFixedThreadPool(10);
    @Override
    public Result queryShopInfo(Long id) {
        // 解决缓存穿透
        // return  this.queryShopInfoHandleCT(id);
        // 解决缓存击穿和缓存穿透（异步锁）
        // return this.queryShopInfoHandleJC(id);
        // 解决缓存击穿 （逻辑过期 + 异步锁）
        return queryShopInfoHandleJC2(id);
    }

    public Result queryShopInfoHandleCT(Long id) {
        // TODO  去redis中查看能不能查到
        String info = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY + id);
        // 判断该类型是有值的字符串
        if (StrUtil.isNotBlank(info)) {
            // json转bean
            Shop shop = JSONUtil.toBean(info, Shop.class);
            return Result.ok(shop);
        } else {
            // 重点 如果从redis取出来是""，说明是为了防止缓存穿透加的值，店铺是不存在的
            if (Objects.equals(info, "")) {
                return Result.fail("店铺不存在");
            }
            // TODO redis中查不到去数据库查
            Shop shopInfo = getById(id);
            if (shopInfo == null) {
                // 重点 为了防止缓存穿透，在数据库查不到的时候在缓存加一个值
                stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, "", 2, TimeUnit.MINUTES);
                return Result.fail("店铺不存在");
            }
            // TODO 数据库中查到了就存入redis，查不到就返回该商品不存在
            String shopStr = JSONUtil.toJsonStr(shopInfo);
            stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, shopStr, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
            return Result.ok(shopInfo);
        }
    }

    // 解决缓存击穿（里面也融合了解决缓存穿透）
    public Result queryShopInfoHandleJC(Long id) {
        // TODO  去redis中查看能不能查到
        String info = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY + "id");
        // 判断该类型是有值的字符串
        if (StrUtil.isNotBlank(info)) {
            // json转bean
            Shop shop = JSONUtil.toBean(info, Shop.class);
            return Result.ok(shop);
        } else {
            // 获取锁
            try {
                boolean isGet = tryGetLock(id);
                if (isGet) {
                    // 再次查缓存，防止有线程已经用过锁把redis写入了
                    String info2 = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY + id);
                    // 也是要的，防止其他线程数据库查不到，然后建立了一个临时的空值key
                    if (Objects.equals(info2, "")) {
                        return Result.fail("店铺不存在");
                    }
                    // 说明缓存有值了
                    if (info2 != null) {
                        Shop shop = JSONUtil.toBean(info2, Shop.class);
                        return Result.ok(shop);
                    }
                    // 查询数据库
                    Shop shopInfo = getById(id);
                    // 模拟写入redis的延迟
                    Thread.sleep(200);
                    if (shopInfo == null) {
                        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, "", 2, TimeUnit.MINUTES);
                        return Result.fail("店铺不存在");
                    }
                    // 数据库查到了
                    String shopStr = JSONUtil.toJsonStr(shopInfo);
                    // 写入redis
                    stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, shopStr, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
                    return Result.ok(shopInfo);
                } else {
                    Thread.sleep(50);
                    // 再次查找
                    return queryShopInfoHandleJC(id);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                // 有异常也要删除锁
                deleteLock(RedisConstants.LOCK_SHOP_KEY + id);
            }
        }
    }

    // 逻辑过期解决缓存击穿（不需要考虑缓存穿透）
    public Result queryShopInfoHandleJC2(Long id) {
        // TODO  去redis中查看能不能查到
        String info = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY + "id");
        // 判断该类型是不是`有值的字符串`
        if (StrUtil.isBlank(info)) {
            return Result.fail("信息不存在");
        } else {
            // 查看key有没有逻辑过期
            RedisData redisData = JSONUtil.toBean(info, RedisData.class);
            // 只返回shop数据，不返回redis的逻辑过期时间
            Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
            if (redisData.getExpireTime().isAfter(LocalDateTime.now())) {
               return Result.ok(shop);
            }else{

                    // 过期了,重新查数据库写入redis
                    // - 获取互斥锁
                    boolean flag = tryGetLock(id);
                    if(!flag){
                        // 获取锁失败返回旧数据
                      return  Result.ok(redisData);
                    }
                    // 重点 再次查找redis有没有过期（可能在这个线程判断过期之后被其他线程抢走之后又重新写入redis了）
                    String info2 = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY + "id");
                    RedisData redisData2 = JSONUtil.toBean(info2, RedisData.class);
                    Shop shop2 = JSONUtil.toBean((JSONObject) redisData2.getData(), Shop.class);
                    if(redisData.getExpireTime().isAfter(LocalDateTime.now())){
                        return Result.ok(shop2);
                    }
                    // 使用线程池开启一个线程去做这个更新redis操作
                    CACHE_REBUILD_EXRCUTOR.submit(() ->{
                        // 查询数据库
                        Shop shopInfo = addExpireRedis(id);
                        return Result.ok(shopInfo);
                    });
                    // 开启线程处理自己就返回旧数据
                return Result.ok(shop);
            }
        }
    }

    @Override
    // 加上事务处理，这个方法一旦出错，mysql就会触发回滚机制
    @Transactional
    public Result updateShop(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺id不能为空");
        }
        // 更新数据库，由于mysql有事务的原子性，所以操作有异常能回滚
        updateById(shop);

        // 删除redis中当前店铺的信息，redis的事务只是一串命令的集合要么执行要么不执行，没有回滚的功能，出现问题需要手动解决
        stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY + id);

        return Result.ok("操作成功");
    }

    // 获取互斥锁
    public boolean tryGetLock(Object key) {
        // 采用setnx 没有就操作的方法来充当锁
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(RedisConstants.LOCK_SHOP_KEY + key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
        return BooleanUtil.isTrue(flag);
    }

    // 释放锁
    public void deleteLock(String key) {
        stringRedisTemplate.delete(key);
    }
    // 查询数据库，将查到的数据添加一个逻辑过期时间写进redis
    public Shop addExpireRedis(Long id){
        Shop shopInfo = getById(id);
        RedisData redisData = new RedisData();
        redisData.setData(shopInfo);
        redisData.setExpireTime(LocalDateTime.now().plusMinutes(30));
        String redisDataStr = JSONUtil.toJsonStr(redisData);
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id,redisDataStr);
        return shopInfo ;
    }
}
