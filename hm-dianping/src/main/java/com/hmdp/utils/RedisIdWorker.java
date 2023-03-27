package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * @program: hm-dianping
 * @description: redis生成全局ID
 * @author: 作者
 * @create: 2023-01-16 21:16
 */

@Component
public class RedisIdWorker {

    @Resource
    public StringRedisTemplate stringRedisTemplate;

    public RedisIdWorker(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /*
        开始时间戳
         */
    private static final long BEGIN_TIMESTAMP=1640995200L;
    /*
    序列号位数
     */
    private static final int COUNT_BITS= 32;


    public long nestId(String keyprefix){
        //生成时间戳
        long second = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);
        long timestamp=second-BEGIN_TIMESTAMP;

        //生成序列号
        String date=LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        //自增
        long cout=stringRedisTemplate.opsForValue().increment("icr:"+keyprefix+":"+date);

        return timestamp << COUNT_BITS | cout;

    }
}
