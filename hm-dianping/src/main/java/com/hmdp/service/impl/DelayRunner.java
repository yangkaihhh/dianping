package com.hmdp.service.impl;

import cn.hutool.extra.spring.SpringUtil;
import com.hmdp.service.IVoucherOrderService;
import com.sun.org.apache.bcel.internal.generic.NEW;
import org.redisson.api.RBlockingQueue;
import org.redisson.api.RedissonClient;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

/**
 * @program: hm-dianping
 * @description:
 * @author: 作者
 * @create: 2023-02-09 15:51
 */

@Component
public class DelayRunner implements CommandLineRunner {
//@Component
//public class DelayRunner {
    @Resource
    private RedissonClient redissonClient;
    @Resource
    private IVoucherOrderService iVoucherOrderService;
    @Override
    public void run(String... args) throws Exception {
        new Thread(() ->
        {
            System.out.println("111111111");
            RBlockingQueue<Long> blockingFairQueue = redissonClient.getBlockingQueue("delay_queue_call");
            // 开启客户端监听（必须调用），否者系统重启时拿不到已过期数据，要等到系统第一次调用getDelayedQueue方法时才能开启监听
            redissonClient.getDelayedQueue(blockingFairQueue);
            while (true) {
                Long take = null;
                try {
                    take = blockingFairQueue.take();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                if (take == null) {
                    continue;
                }
                boolean b = iVoucherOrderService.removeById(take);
                System.out.println("是否删除  ID  为" + take + "的订单--------->:" + b);
            }
        }).start();
    }
    }

