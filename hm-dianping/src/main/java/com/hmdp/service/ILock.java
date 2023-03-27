package com.hmdp.service;

public interface ILock {
    public boolean tryLock(long time);
    public void unlock();
}
