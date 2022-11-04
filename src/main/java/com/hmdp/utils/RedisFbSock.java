package com.hmdp.utils;

/**
 * @author Mr.Wang
 * @version 1.0
 * @since 1.8
 */
public interface RedisFbSock {
    // 尝试获取redis分布式锁,由于每个业务的锁过期时间可能不一样
    boolean tryGetLock(long timeout);

    // 释放锁
    void unLock();
}
