package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisFbSockImp;
import com.hmdp.utils.RedisGlobalID;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private RedisGlobalID redisGlobalID;
    @Resource
    private VoucherOrderServiceImpl voucherOrderService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    // 用乐观锁悲观锁的实现方式（只适用非集群）
    public Result seckillOneCoupon1(Long id) {
        // 查规定时间
        SeckillVoucher coupon = seckillVoucherService.getById(id);
        if(coupon.getEndTime().isBefore(LocalDateTime.now()) || coupon.getBeginTime().isAfter(LocalDateTime.now())){
            return Result.fail("不在规定时间");
        }
        // 查库存
        if(coupon.getStock() <= 0){
            return Result.fail("库存不足了" + coupon.getStock());
        }
       /* // 重点 这里如果线程被抢走，就会出现超卖问题，这里用乐观锁的CAS法，只判断库存是否和原先查到的一致来判断线程是否安全，而由于业务场景，就算有不安全问题，库存只要存在即可扣减
        // 重点 数据库是默认加锁的，不存在数据库取到的值有线程安全问题

        // 有库存就扣减库存   .eq("voucher_id", id).gt("stock",coupon.getStock())相当于where voucher_id = id and stock > 0
        boolean success = seckillVoucherService.update().setSql("stock = stock - 1").eq("voucher_id", id).gt("stock",0).update();
        if(!success){
            return Result.fail("库存扣减失败");
        }*/
        Long userId = UserHolder.getUser().getId();
        // Long的toString是new了一个新的对象创建的，所以地址还是不一样的，需要用实习生方法转
        // 重点 这么写是先提交事务再释放悲观锁（√）  如果在@Transactional里面加悲观锁就会导致先释放锁再提交事务，一旦释放锁，未提交事务前又要出问题 （×）
        synchronized (userId.toString().intern()){
            return voucherOrderService.createCouponOrder(id);
        }
    }
    // 使用Redis分布式锁的方式
    @Override
    public Result seckillOneCoupon(Long id) {
        // 查规定时间
        SeckillVoucher coupon = seckillVoucherService.getById(id);
        if(coupon.getEndTime().isBefore(LocalDateTime.now()) || coupon.getBeginTime().isAfter(LocalDateTime.now())){
            return Result.fail("不在规定时间");
        }
        // 查库存
        if(coupon.getStock() <= 0){
            return Result.fail("库存不足了" + coupon.getStock());
        }
        Long userId = UserHolder.getUser().getId();

        // 获取锁  opoo -- one people one order 一人一单业务
        RedisFbSockImp redisFbSockImp = new RedisFbSockImp("opoo:" + userId, stringRedisTemplate);
        boolean isGetLock = redisFbSockImp.tryGetLock(10);
        if(!isGetLock){
            return Result.fail("请勿重复下单");
        }
        // 不用catch处理异常，也会让事务生效
        try {
            return voucherOrderService.createCouponOrder(id);
        } finally {
            redisFbSockImp.unLock();
        }
    }
    // 事务所有异常都会回滚，默认是运行时异常才会回滚
    @Transactional(rollbackFor = Exception.class)
    public Result createCouponOrder(Long id){
        // ----   一人一单，为了防止一个人使用工具狂抢造成的安全问题，需要加锁
        // 查询优惠券列表是否存在该人
        Long userId = UserHolder.getUser().getId();
        Integer count = query().eq("user_id", userId).eq("voucher_id", id).count();
        if(count > 0){
            return Result.fail("只能抢一次");
        }
        // 扣减库存（在这里同时解决了超卖问题）
        boolean success = seckillVoucherService.update().setSql("stock = stock - 1").eq("voucher_id", id).gt("stock",0).update();
        if(!success){
            return Result.fail("库存扣减失败");
        }
        // 创建优惠券订单
        VoucherOrder voucherOrder = new VoucherOrder();
        // 订单id
        long couponOrderId = redisGlobalID.nextId("coupon_order");
        voucherOrder.setId(couponOrderId);
        // 用户id
        voucherOrder.setUserId(userId);
        // 优惠券id
        voucherOrder.setVoucherId(id);
        // 其他字段应该有默认值
        save(voucherOrder);
        return Result.ok();
    }
}
