package com.hmdp.utils;

import org.redisson.api.RBlockingDeque;
import org.redisson.api.RBlockingQueue;
import org.redisson.api.RDelayedQueue;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestParam;

import javax.annotation.Resource;

import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.DELAY_QUEU;

/**
 * @program: hm-dianping
 * @description: 延时队列
 * @author: 作者
 * @create: 2023-02-09 14:45
 */

@Component
public class RedisDelayQueueUtil {


    @Autowired
    private RedissonClient redissonClient;

    public void addDelay(Long id) {
        RBlockingQueue<Long> blockingFairQueue = redissonClient.getBlockingQueue("delay_queue_call");
        RDelayedQueue<Long> delayedQueue = redissonClient.getDelayedQueue(blockingFairQueue);
        delayedQueue.offer(id, 10, TimeUnit.SECONDS);
        // 不要调用下面的方法,否者会导致消费不及时
//        delayedQueue.destroy();
    }

}
