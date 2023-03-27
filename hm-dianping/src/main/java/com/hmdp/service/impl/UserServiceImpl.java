package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        //1.校验手机号
        if(RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号无效");
        }
        //2.符合，生成验证码
        String randomNumbers = RandomUtil.randomNumbers(6);
        //3.保存验证码到session   设置有效期  2分钟
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY+phone,randomNumbers,LOGIN_CODE_TTL, TimeUnit.MINUTES);
        //4.发送验证码
        log.debug("验证码发送成功-----》"+randomNumbers);

        return Result.ok(randomNumbers);
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {

        String code = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY+loginForm.getPhone());
        //1.校验验证码格式
        if (RegexUtils.isCodeInvalid(loginForm.getCode())){
            return Result.fail("验证码格式错误");
        }
        //1.校验手机号
        if(RegexUtils.isPhoneInvalid(loginForm.getPhone())) {
            return Result.fail("手机号无效");
        }
        //2.校验验证码是否一致
        if (loginForm.getCode()==null || !loginForm.getCode().equals(code)){
            return Result.fail("验证码错误");
        }
        //3.一致，根据用户Phone查询用户信息   select * from tb_user where phone=?
        User user = query().eq("phone", loginForm.getPhone()).one();
        //判断用户是否存在
        if (user==null){
            //1.创建用户
            user=new User();
            user.setPhone(loginForm.getPhone());
            user.setNickName(USER_NICK_NAME_PREFIX+RandomUtil.randomString(4));
            save(user);
        }
        //用户加入redis
        //随机生成token，作为登录令牌
        String token= UUID.randomUUID().toString(true);
        //将user转成hash存储
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> map = BeanUtil.beanToMap(userDTO,new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true).
                        setFieldValueEditor((k,v)->v.toString()));
        //存储
        stringRedisTemplate.opsForHash().putAll(LOGIN_USER_KEY+token,map);
        //设置token有效期   120分钟有效
        stringRedisTemplate.expire(LOGIN_USER_KEY+token,LOGIN_USER_TTL,TimeUnit.MINUTES);
        //返回token

        return Result.ok(token);
    }




}
