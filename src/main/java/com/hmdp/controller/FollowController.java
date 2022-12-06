package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.service.IFollowService;
import com.hmdp.service.impl.FollowServiceImpl;
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
    private IFollowService followService;
    // 关注、取关
    @PutMapping("/{id}/{isFocus}")
    public Result focus(@PathVariable("id") Long id,@PathVariable("isFocus") Boolean flag){
        return followService.isFoucus(id,flag);
    }

    // 查有没有关注该用户
    @GetMapping("/or/not/{id}")
    public Result isFocus(@PathVariable("id") Long id){
        return followService.focus(id);
    }

}
