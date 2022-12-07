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
import org.redisson.mapreduce.Collector;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
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
    public Result focus(Long id) {
        // 获取登录用户
        Long userId = UserHolder.getUser().getId();
        // 查看有没有关注
        Integer count = query().eq("user_id", userId).eq("follow_user_id", id).count();
        return Result.ok(count > 0);
    }

    @Override
    public Result isFoucus(Long id, Boolean flag) {
        // 获取登录用户
        Long userId = UserHolder.getUser().getId();
        if (flag){
            // 新增数据
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(id);
            boolean isSuccess = save(follow);
            // 成功存到redis，准备利用redis的set集合可以获取并集功能
            if(isSuccess){
                stringRedisTemplate.opsForSet().add("follows:" + userId, String.valueOf(id));
            }
        }else{
            // 取关
            boolean isSuccess = remove(new QueryWrapper<Follow>().eq("user_id", userId).eq("follow_user_id", id));
            if (isSuccess){
                stringRedisTemplate.opsForSet().remove("follows:" + userId, String.valueOf(id));
            }
        }
        return Result.ok();
    }

    @Override
    public Result commonFocus(Long id) {
        // 获取当前用户
        Long userId = UserHolder.getUser().getId();
        // 求交集
        String key1 = "follows:" + userId;
        String key2 = "follows:" + id;
        Set<String> commonFocusId = stringRedisTemplate.opsForSet().intersect(key1, key2);
        List<Long> ids = commonFocusId.stream().map(Long::valueOf).collect(Collectors.toList());
        // 查询用户
        // 将user对象（里面有属性赋值了） 复制给UserDTO的对象，实现只返回不敏感信息
        List<UserDTO> users = userService.listByIds(ids).stream().map(user -> BeanUtil.copyProperties(user, UserDTO.class)).collect(Collectors.toList());
        return Result.ok(users);
    }
}
