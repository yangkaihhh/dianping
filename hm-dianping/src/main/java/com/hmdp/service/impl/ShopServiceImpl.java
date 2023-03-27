package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.LOCK_SHOP_TTL;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedisClient redisClient;


    @Override
    public Result queryById(Long id) {
        //缓存穿透
        //Shop shop = queryWithPass(id);
        Shop shop=redisClient
                .queryWithPass(RedisConstants.CACHE_SHOP_KEY,id,Shop.class, this::getById,RedisConstants.CACHE_SHOP_TTL,TimeUnit.MINUTES);


        //互斥锁解决缓存击穿
        //Shop shop = queryWithMutex(id);

        //逻辑过期解决缓存击穿
        //Shop shop = queryWithLogic(id);

        if (shop==null){
            return Result.fail("店铺不存在");
        }
        return Result.ok(shop);
    }

    public void saveShopToRedis(Long id,Long expireSeconds){
        //查询商品信息
        Shop shop = getById(id);

        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        //设置逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        redisData.setData(shop);
        //写入redis
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY+id,JSONUtil.toJsonStr(redisData));
    }

    public Shop queryWithMutex(Long id){
        //1.从redis获取用户信息缓存
        String shopJson = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY + id);
        //2.判断是否在缓存中
        if(StrUtil.isNotBlank(shopJson)){
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }
        if (shopJson != null){
            return null;
        }

        //3.存在返回，不存在查数据库 ,再次判断是否存在

        //实现缓存重建
        //..1获取互斥锁
        String lockkey=RedisConstants.LOCK_SHOP_KEY+id;
        Shop shop = null;
        try {
            boolean b = tryLock(lockkey);
            //..2判断是否获取成功
            if (!b){
                //..3失败则失眠并重试
                Thread.sleep(50);
                return queryWithMutex(id);
            }
            //..4成功  查数据库
            shop = getById(id);

            //模拟时间查询长
            Thread.sleep(200);

            if (shop==null){
                //空值写入redis缓存
                stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id,"",RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            //4.数据库存在写入缓存
            String jsonStr = JSONUtil.toJsonStr(shop);
            stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id,jsonStr,RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);

        } catch (InterruptedException e) {
            e.printStackTrace();
        }finally {
            //释放互斥锁
            unLock(lockkey);
        }
        //5.返回
        return shop;
    }

    public Shop queryWithLogic(Long id){
        //1.从redis获取用户信息缓存
        String shopJson = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY + id);
        //2.判断是否在缓存中
        if(StrUtil.isBlank(shopJson)){
            return null;
        }

        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        JSONObject redisDataData = (JSONObject)redisData.getData();
        Shop shop = JSONUtil.toBean(redisDataData, Shop.class);

        if (redisData.getExpireTime().compareTo(LocalDateTime.now())>0){
            return shop;
        }

        boolean tryLock = tryLock(RedisConstants.LOCK_SHOP_KEY + id);

        if (!tryLock){
            return shop;
        }
        CACHE_REDU.submit(()->{
            try {
                this.saveShopToRedis(id,20L);
            } catch (Exception e) {
                e.printStackTrace();
            }finally {
                unLock(RedisConstants.LOCK_SHOP_KEY + id);
            }

        });

        //5.返回
        return shop;
    }

    private static final ExecutorService CACHE_REDU=Executors.newFixedThreadPool(10);

    public Shop queryWithPass(Long id){
        //1.从redis获取用户信息缓存
        String shopJson = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY + id);
        //2.判断是否在缓存中
        if(StrUtil.isNotBlank(shopJson)){
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }
        if (shopJson != null){
            return null;
        }

        //3.存在返回，不存在查数据库 ,再次判断是否存在
        Shop shop = getById(id);
        if (shop==null){
            //空值写入redis缓存
            stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id,"",RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        //4.数据库存在写入缓存
        String jsonStr = JSONUtil.toJsonStr(shop);
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id,jsonStr,RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //5.返回
        return shop;
    }

    @Override
    @Transactional
    public Result updateShop(Shop shop) {
        Long id = shop.getId();
        if (id==null){
            return Result.fail("商铺id不能为空");
        }
        //更新数据库
        updateById(shop);
        //删除缓存
        stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY + id);
        return Result.ok();
    }

    private boolean tryLock(String key){
        Boolean aBoolean = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(aBoolean);
    }

    private void unLock(String key){
        stringRedisTemplate.delete(key);
    }

    public void time(){
        LocalDateTime a = LocalDateTime.now().plusSeconds(10L);
        LocalDateTime b = LocalDateTime.now().plusSeconds(20L);
        String s = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY + 1);
        RedisData redisData = JSONUtil.toBean(s, RedisData.class);
        LocalDateTime expireTime = redisData.getExpireTime();

        int i = redisData.getExpireTime().compareTo(LocalDateTime.now());

        System.out.println("++++++++++"+a.compareTo(b));
        System.out.println(i);
    }


}
