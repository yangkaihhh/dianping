package com.hmdp.utils;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.events.Event;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * @program: hm-dianping
 * @description: redis工具
 * @author: 作者
 * @create: 2023-01-12 00:12
 */

@Slf4j
@Component
public class RedisClient {

    private StringRedisTemplate stringRedisTemplate;


    public RedisClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void set(String key, Object value, Long time, TimeUnit unit){
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value),time,unit);
    }

    public void setLogic(String key, Object value, Long time, TimeUnit unit){
        //设置逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        redisData.setData(value);
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }


    public <R, ID> R queryWithPass(String KeyPrefix , ID id , Class<R> type , Function<ID, R> dbback,Long time, TimeUnit unit){
        //1.从redis获取用户信息缓存
        String shopJson = stringRedisTemplate.opsForValue().get(KeyPrefix + id);
        //2.判断是否在缓存中
        if(StrUtil.isNotBlank(shopJson)){
            R r = JSONUtil.toBean(shopJson, type);
            return r;
        }
        if (shopJson != null){
            return null;
        }

        //3.存在返回，不存在查数据库 ,再次判断是否存在
        R r = dbback.apply(id);
        if (r==null){
            //空值写入redis缓存
            stringRedisTemplate.opsForValue().set(KeyPrefix + id,"",RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        //4.数据库存在写入缓存
        String jsonStr = JSONUtil.toJsonStr(r);
        this.set(KeyPrefix+id,r,time,unit);
        //5.返回
        return r;
    }

}
