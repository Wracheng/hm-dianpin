package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import com.hmdp.utils.RedisGlobalID;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.connection.stream.*;
import lombok.extern.slf4j.Slf4j;
import org.redisson.Redisson;
import org.redisson.api.RLock;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private RedisGlobalID redisGlobalID;
    // @Resource
    private VoucherOrderServiceImpl voucherOrderService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private Redisson redisson;
    // static 提前定义好，就不用每次释放锁都来创建
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT = new  DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }
    // 当有任务的时候执行，没有的时候会阻塞等待
    // private final BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();
    @PostConstruct
    // 项目启动时会执行该方法开启线程
    private void init(){
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    };
    public class VoucherOrderHandler implements Runnable{

        @Override
        public void run() {
            while (true){

                try {
                    // VoucherOrder take = orderTasks.take();
                    // handleOrder(take);
                    // 获取消息队列的信息
                    Consumer from = Consumer.from("g1", "c1");
                    StreamReadOptions block = StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2));
                    StreamOffset<String> stringStreamOffset = StreamOffset.create("stream.orders", ReadOffset.lastConsumed());
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(from, block, stringStreamOffset);
                    if(list == null || list.isEmpty()){
                        continue;
                    }
                    // 解析消息中的信息
                    MapRecord<String, Object, Object> entries = list.get(0);
                    Map<Object, Object> value = entries.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);

                    // 下单
                    handleOrder(voucherOrder);
                    // ack确认
                    stringRedisTemplate.opsForStream().acknowledge("stream.orders","g1",entries.getId());
                } catch (Exception e) {
                    // 出现异常需要手动去PEL列表处理信息
                    handlePLENotice();
                } finally {
                }

            }
        }
    }

    private void handlePLENotice() {
        while (true){

            try {
                Consumer from = Consumer.from("g1", "c1");
                StreamReadOptions block = StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2));
                StreamOffset<String> stringStreamOffset = StreamOffset.create("stream.orders", ReadOffset.from("0"));
                List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(from, block, stringStreamOffset);
                if(list == null || list.isEmpty()){
                    // PEL中没有异常消息
                    break;
                }

                MapRecord<String, Object, Object> entries = list.get(0);
                Map<Object, Object> value = entries.getValue();
                VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);

                handleOrder(voucherOrder);

                stringRedisTemplate.opsForStream().acknowledge("stream.orders","g1",entries.getId());
            } catch (Exception e) {
                log.error("处理有异常" + e);

            } finally {
            }

        }
    }

    // 用乐观锁悲观锁的实现方式（只适用非集群）
    public Result seckillOneCoupon1(Long id) {
        // 查规定时间
        SeckillVoucher coupon = seckillVoucherService.getById(id);
        if (coupon.getEndTime().isBefore(LocalDateTime.now()) || coupon.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("不在规定时间");
        }
        // 查库存
        if (coupon.getStock() <= 0) {
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
        synchronized (userId.toString().intern()) {
            return voucherOrderService.createCouponOrder(id);
        }
    }

    // 使用Redis分布式锁的方式
    public Result seckillOneCoupon2(Long id) throws InterruptedException {
        // 查规定时间
        SeckillVoucher coupon = seckillVoucherService.getById(id);
        if (coupon.getEndTime().isBefore(LocalDateTime.now()) || coupon.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("不在规定时间");
        }
        // 查库存
        if (coupon.getStock() <= 0) {
            return Result.fail("库存不足了" + coupon.getStock());
        }
        Long userId = UserHolder.getUser().getId();

        /*// 获取锁  opoo -- one people one order 一人一单业务
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
        }*/

        // 使用Redisson获取锁
        RLock lock = redisson.getLock("lock:opoo:" + userId);
        // lock.tryLock(1, 10, TimeUnit.SECONDS);  1是失败等待多少时间重试一次重试成功就成功，失败就失败，10是锁的超时时间，默认30s
        boolean isGetLock = lock.tryLock(1, 10, TimeUnit.SECONDS);
        if (!isGetLock) {
            return Result.fail("请勿重复下单");
        }
        try {
            return voucherOrderService.createCouponOrder(id);
        } finally {
            lock.unlock();
        }
    }

    // 使用lua脚本 + 线程 （提前返回成功消息，后续用redis的stream消息队列慢慢创建订单）
    @Override
    public Result seckillOneCoupon(Long id){

        // 优惠券开始时间结束时间这里没写

        Long userId = UserHolder.getUser().getId();
        long orderId = redisGlobalID.nextId("coupon_order");
        // 执行lua脚本（判断购买资格和添加信息）
        Long execute = stringRedisTemplate.execute(SECKILL_SCRIPT, Collections.emptyList(), id.toString(), userId.toString(),String.valueOf(orderId));
        int res = execute.intValue();
        if(res != 0){
            return Result.fail(res == 1 ? "库存不足" : "请勿重复下单");
        }
        // // 将创建订单交给另外一个线程的消息队列
        // VoucherOrder voucherOrder = new VoucherOrder();
        // voucherOrder.setVoucherId(id);
        // voucherOrder.setId(orderId);
        // voucherOrder.setUserId(userId);
        // // 添加到消息队列
        // orderTasks.add(voucherOrder);
        return Result.ok(orderId);

    }

    // 事务所有异常都会回滚，默认是运行时异常才会回滚
    @Transactional(rollbackFor = Exception.class)
    public Result createCouponOrder(Long id) {
        // ----   一人一单，为了防止一个人使用工具狂抢造成的安全问题，需要加锁
        // 查询优惠券列表是否存在该人
        Long userId = UserHolder.getUser().getId();
        Integer count = query().eq("user_id", userId).eq("voucher_id", id).count();
        if (count > 0) {
            return Result.fail("只能抢一次");
        }
        // 扣减库存（在这里同时解决了超卖问题）
        boolean success = seckillVoucherService.update().setSql("stock = stock - 1").eq("voucher_id", id).gt("stock", 0).update();
        if (!success) {
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

    public void handleOrder(VoucherOrder voucherOrder){
        Long userId = voucherOrder.getUserId();
        RLock lock = redisson.getLock("lock:order:" + userId);
        boolean b = lock.tryLock();
        // 只是兜底，可不判断
        if(!b){
            log.error("不能重复下单");
            return;
        }
        try{
            // 这里为什么不用加sync，因为在lua脚本里已经保证一人一单了，不需要加锁，这里只需要处理符合一人一单规则的创建订单就行了
            voucherOrderService.createCouponOrder(voucherOrder.getId());
        }finally {
            lock.unlock();
        }
    }
}
