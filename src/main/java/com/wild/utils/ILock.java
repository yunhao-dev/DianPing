package com.wild.utils;

/**
 * @description:
 * @Author: yunhao_dev
 * @Date: 2024/8/6 5:06
 */
public interface ILock {
    /**
     * 尝试获取锁
     * @param timeoutSec 锁持有的时间，过期后自动释放
     * @return true代表获取锁成功，false代表获取失败
     */
    boolean tryLock(long timeoutSec);

    /**
     * 释放锁
     */
    void unlock();
}
