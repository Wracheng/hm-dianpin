package com.hmdp.config;

import com.hmdp.interceptor.LoginInterceptor;
import com.hmdp.interceptor.RefalshInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;

/**
 * @author Mr.Wang
 * @version 1.0
 * @since 1.8
 */
@Configuration
public class MvcConfig implements WebMvcConfigurer {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    // 注册一个LoginInterceptor
    public void addInterceptors(InterceptorRegistry registry) {
        // 默认拦截所有请求，登录后不管有没有需要带token请求都要刷新token时长
        registry.addInterceptor(new RefalshInterceptor(stringRedisTemplate));
        // 这个仅仅用来判断有没有登录，注意本项目场景是没做登录不可以访问任何页面的前提的
        registry.addInterceptor(new LoginInterceptor()).excludePathPatterns("/user/code", "/user/login", "/blog/hot", "/shop/**", "/shop-type/**", "/voucher/**");
    }
}
