package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
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
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {
    @Resource
    private IUserService userService;
    @Resource
    private IFollowService followService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryById(long id) {
        Blog blog = getById(id);
        if(blog == null){
            return Result.fail("笔记不存在");
        }
        // 添加用户信息
        User user = userService.getById(blog.getUserId());
        blog.setIcon(user.getIcon());
        blog.setName(user.getNickName());
        isLiked(blog);
        return Result.ok(blog);
    }
    @Override
    public Result queryHot(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog ->{
            User user = userService.getById(blog.getUserId());
            blog.setName(user.getNickName());
            blog.setIcon(user.getIcon());
            isLiked(blog);
        });
        return Result.ok(records);
    }

    @Override
    public Result likeBlogRank(Long id) {
        // 判断用户是否点赞（这里先只查top5）
        String key = "blog:liked" + id;
        Set<String> range = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        if(range == null || range.isEmpty()){
            // 返回空的lsit
            return Result.ok(Collections.emptyList());
        }
        // 解析出其中的用户 此处用了jdk1.8的流处理（类似于js的map） ,collect(Collectors.toList())是固定写法可以转成List
        List<Long> ids = range.stream().map(Long::valueOf).collect(Collectors.toList());
        String idStr = StrUtil.join(",",ids);

        // 根据用户id查询用户 where id in (5 , 1) order by field( id , 5, 1) ,因为in 取出来是无序的，所以需要加上order by
        List<UserDTO> userDtos = userService.query().in("id", ids).last("order by field(id," + idStr + ")").list()
                .stream().map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(userDtos);
    }

    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        // 找到当前用户
        Long userId = UserHolder.getUser().getId();
        String key = "feed:" + userId;
        // 查询收件箱   查到的是博客的id和一个score（在这里是时间戳），offset
        // zrangebyscore key名 最小排序值 最大排序值 [withscores] [limit 从第index（从0开始）个数据开始 取n个数据]
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet().reverseRangeByScoreWithScores(key, 0, max, offset, 2);
        if(typedTuples ==null || typedTuples.isEmpty()){
            // 本身就没有信息
            return Result.ok();
        }

        long minTime = 0;
        // 相同的score（时间戳）重复了几次, 手动让os移动，因为在zset中,相同的score需要指定是哪一个score，需要用到偏移量
        int os = 1;
        // 专门存放收件箱查到的博客id
        ArrayList<Long> ids = new ArrayList<>(typedTuples.size());
        // 解析出blogId、minTime、offset
        for(ZSetOperations.TypedTuple<String> item : typedTuples){
            // 获取id并添加到ids List集合中
            ids.add(Long.valueOf(item.getValue()));

            // 获取分数（时间戳）
            long time = item.getScore().longValue();
            // 后面查到的肯定比前面查到的时间戳小于或等于
            if(time == minTime){
                os ++;
            }else{
                minTime = time;
                os = 1;
            }

        }

        // 根据id查询对应的文章
        String idStr = StrUtil.join(",", ids);
        List<Blog> blogs = query().in("id",ids).last("ORDER BY FIELD(id," + idStr + ")").list();

        // 判断关注
        for(Blog item : blogs){
            // 判断这篇文章该用户是否点赞
            isLiked(item);
            // 查看这篇文章相关的点赞用户
            likeBlogRank(item.getId());
        }

        ScrollResult scrollResult = new ScrollResult();
        scrollResult.setList(blogs);
        scrollResult.setOffset(os);
        scrollResult.setMinTime(minTime);

        return Result.ok(scrollResult);
    }

    @Override
    public Result saveBlog(Blog blog) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 保存探店博文
        boolean isSuccess = save(blog);
        if (!isSuccess){
            return Result.fail("新增笔记失败");
        }
        // 查询笔记作者的所有粉丝
        List<Follow> follows = followService.query().eq("follow_user_id", user.getId()).list();
        // 推送笔记id给所有粉丝
        for(Follow item : follows){
            Long userId = item.getUserId();
            String key = "feed:" + userId;
            stringRedisTemplate.opsForZSet().add(key, blog.getId().toString(),System.currentTimeMillis());
        }
        // 返回id
        return Result.ok(blog.getId());
    }

    // 判断用户是否点赞，存到Blog Bean中
    public void isLiked(Blog blog){
        // 获取登录用户
        Long userId = UserHolder.getUser().getId();
        String key = "blog:liked" + blog.getId();
        // 判断用户是否点赞
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        blog.setIsLike(score != null);
    }
    @Override
    // 点赞或取消点赞
    public Result likeBlog(Long id) {
        // 获取登录用户
        Long userId = UserHolder.getUser().getId();
        String key = "blog:liked" + userId;
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());

        // 如果未点赞，可以点赞
        if(score == null){
            // 数据库点赞数 + 1
            boolean isSuccess = update().setSql("liked = liked + 1").eq("id", id).update();
            if(isSuccess){
                // 保存用户到Redis的set集合
                stringRedisTemplate.opsForSet().add(key, String.valueOf(userId));
            }

        }else{
            // 如果已点赞取消点赞
            // 数据库点赞数 - 1
            boolean isSuccess2 = update().setSql("liked = liked - 1").eq("id", id).update();
            // 移除Redis的set集合中该用户
            if(isSuccess2){
                // 保存用户到Redis的set集合
                stringRedisTemplate.opsForSet().remove(key, String.valueOf(userId));
            }

        }

        return Result.ok("操作成功");
    }

}
