package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSON;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.google.gson.Gson;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Resource
    private  StringRedisTemplate stringRedisTemplate;
    @Resource
    private IShopTypeService typeService;
    private String TypeList="typelistkey";

    @Override
    public List<ShopType> queryTypeList() {
        //1.redis中查询是否有
        if (StrUtil.isNotBlank(stringRedisTemplate.opsForValue().get(TypeList))){
            String typejson = stringRedisTemplate.opsForValue().get(TypeList);
            List<ShopType> shopTypes = JSONUtil.toList(typejson, ShopType.class);
            return shopTypes;
        }else {
            //2.没有则到数据库中查
            List<ShopType> typeList = typeService
                .query().orderByAsc("sort").list();
            //3.写入缓存中
            String typejson = JSONUtil.toJsonStr(typeList);
            stringRedisTemplate.opsForValue().set(TypeList,typejson);

            return typeList;
        }
    }
}
