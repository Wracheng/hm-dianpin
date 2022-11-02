package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisGlobalID;
import com.hmdp.utils.UserHolder;
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
    @Override
    @Transactional
    public Result seckillOneCoupon(Long id) {
        // 查规定时间
        SeckillVoucher coupon = seckillVoucherService.getById(id);
        if(coupon.getEndTime().isBefore(LocalDateTime.now()) || coupon.getBeginTime().isAfter(LocalDateTime.now())){
            return Result.fail("不在规定时间");
        }
        // 查库存
        if(coupon.getStock() <= 0){
            return Result.fail("库存不足");
        }
        // 重点 这里如果线程被抢走，就会出现超卖问题，这里用乐观锁的CAS法，只判断库存是否和原先查到的一致来判断线程是否安全，而由于业务场景，就算有不安全问题，库存只要存在即可扣减
        // 重点 数据库是默认加锁的，不存在数据库取到的值有线程安全问题

        // 有库存就扣减库存   .eq("voucher_id", id).gt("stock",coupon.getStock())相当于where voucher_id = id and stock > 0
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
        voucherOrder.setUserId(UserHolder.getUser().getId());
        // 优惠券id
        voucherOrder.setVoucherId(id);
        // mybatis save 可以只需要传入各表的主键就行，会带上其他信息（还需要去学mybatis plus）
        save(voucherOrder);
        return Result.ok();
    }
}
