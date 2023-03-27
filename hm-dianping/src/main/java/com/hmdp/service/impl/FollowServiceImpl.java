package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.security.Key;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IUserService userService;

    @Override
    public Result follow(Long id, boolean isFollow) {
        //
        Long userId = UserHolder.getUser().getId();
        if (userId==null){
            return Result.ok();
        }
        //
        String key="follow:"+userId;
        if (isFollow){
        Follow follow = new Follow();
        follow.setFollowUserId(id);
        follow.setUserId(userId);
        follow.setCreateTime(LocalDateTime.now());
        boolean save = save(follow);
        if (save){
            stringRedisTemplate.opsForSet().add(key,id.toString());
        }
        }else {
            boolean remove = remove(new QueryWrapper<Follow>().eq("user_id", userId).eq("follow_user_id", id));
            if (remove){
            stringRedisTemplate.opsForSet().remove(key,id.toString());
        }}
        return Result.ok();
    }

    @Override
    public Result isFollow( Long id) {
        //
        Long userId = UserHolder.getUser().getId();
        if (userId==null){
            return Result.ok();
        }

        Integer count = query().eq("user_id", userId).eq("follow_user_id", id).count();
        return Result.ok(count>0);
    }

    @Override
    public Result followCommon(Long id) {
        Long userId = UserHolder.getUser().getId();
        String key1="follow:"+userId;
        String key2="follow:"+id;
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key1, key2);
        List<Long> collect = intersect.stream().map(Long::valueOf).collect(Collectors.toList());
        if (collect==null || collect.isEmpty()){
            return Result.fail("无共同关注");
        }

        List<UserDTO> userDTOS = userService.listByIds(collect).stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(userDTOS);

    }
}
