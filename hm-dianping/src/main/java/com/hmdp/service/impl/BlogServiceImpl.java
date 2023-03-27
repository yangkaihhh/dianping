package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestParam;

import javax.annotation.Resource;
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
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private IBlogService blogService;
    @Resource
    private IUserService userService;
    @Resource
    private IFollowService followService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog -> {
            this.queryBlogUser(blog);
            this.isBloglike(blog);
        });
        return Result.ok(records);
    }

    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }

    @Override
    public Result queryById(Long id) {
        Blog byId = getById(id);
        if(byId==null ){
            return Result.fail("不存在");
        }
        //查询blog相关user
        queryBlogUser(byId);
        //查询是否被点赞
        isBloglike(byId);
        return Result.ok(byId);
    }

    private void isBloglike(Blog byId) {
        //获取登录用户
        Long id1 = UserHolder.getUser().getId();
        if (id1==null){
            return;
        }
        //判断是否点赞过
        String key="blog:lock:" + byId.getId();
        Double score = stringRedisTemplate.opsForZSet().score(key, id1.toString());
        byId.setIsLike(score != null);

    }

    @Override
    public Result likeBlog(Long id) {
        //获取登录用户
        Long id1 = UserHolder.getUser().getId();
        //判断是否点赞过
        String key= RedisConstants.BLOG_LIKED_KEY + id;
        Double score = stringRedisTemplate.opsForZSet().score(key, id1.toString());
        if(score==null) {
            //未点赞，可以点赞
            //数据库  -1
            boolean update = update().setSql("liked = liked + 1").eq("id", id).update();
            //保存信息到redis
            if(update){
                stringRedisTemplate.opsForZSet().add(key,id1.toString(),System.currentTimeMillis());
            }
        }else {
            //点赞过，，取消点赞
            boolean update = update().setSql("liked = liked - 1").eq("id", id).update();
            //删除在redis中的信息
            if(update){
                stringRedisTemplate.opsForZSet().remove(key,id1.toString());
            }
        }

        return Result.ok();
    }

    @Override
    public Result queryBlogLikes(Long id) {
        Set<String> range = stringRedisTemplate.opsForZSet().range(RedisConstants.BLOG_LIKED_KEY + id, 0, 4);

        if (range==null || range.isEmpty()){
            return Result.ok();
        }

        List<Long> ids = range.stream().map(Long::valueOf).collect(Collectors.toList());

        if (ids.size()>1) {
            String join = StrUtil.join("," + ids);

            List<UserDTO> collect = userService.query()
                    .in("id", ids)
                    .last("order by field (id ," + join + ")").list()
                    .stream()
                    .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                    .collect(Collectors.toList());

            return Result.ok(collect);
        }

        UserDTO userDTO = BeanUtil.copyProperties(userService.getById(StrUtil.toString(ids.get(0))), UserDTO.class);
        return Result.ok(userDTO);
    }

    @Override
    public Result saveBlog(Blog blog) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 保存探店博文
        boolean save = save(blog);
        //获取粉丝
        List<Follow> follow_user_id = followService.query().eq("follow_user_id", user.getId()).list();
        //推送给粉丝
        for (Follow follow:follow_user_id){
            Long id = follow.getUserId();
            String key=RedisConstants.FEED_KEY+id;
            stringRedisTemplate.opsForZSet().add(key,blog.getId().toString(),System.currentTimeMillis());
        }

        // 返回id
        return Result.ok(blog.getId());
    }

    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        return null;
    }


}
