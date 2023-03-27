package com.hmdp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @program: hm-dianping
 * @description:
 * @author: 作者
 * @create: 2023-01-18 00:19
 */

@Configuration
public class RedissonConfig {

    @Bean
    public RedissonClient redissonClient(){
        Config config = new Config();
        //单节点
        config.useSingleServer().setAddress("redis://127.0.0.1:6379").setPassword("aaaa");
        return Redisson.create(config);
    }
}
