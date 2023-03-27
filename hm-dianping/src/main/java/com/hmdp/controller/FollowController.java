package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.service.IFollowService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/follow")
public class FollowController {
    @Resource
    private IFollowService iFollowService;

    @PutMapping("/{id}/{isFollow}")
    public Result follow(@PathVariable("id") Long id,@PathVariable("isFollow") boolean isFollow){
        return iFollowService.follow(id,isFollow);
    }

    @GetMapping("/or/not/{id}")
    public Result isFollow(@PathVariable("id") Long id){
        return iFollowService.isFollow(id);
    }

    @GetMapping("/common/{id}")
    public Result  followCommon(@PathVariable("id") Long id){
        return iFollowService.followCommon(id);
    }

}
