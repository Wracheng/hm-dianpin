package com.hmdp.interceptor;

import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.utils.UserHolder;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/**
 * @author Mr.Wang
 * @version 1.0
 * @since 1.8
 */
// 定义一个拦截器
public class LoginInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        HttpSession session = request.getSession();
        // session中取可以看到以前有没有登录过，但是不怕多个用户请求会覆盖吗（每个用户都是单独的session域）session域、request域又是只有tomcat才有效
        UserDTO userInfo = (UserDTO) session.getAttribute("userInfo");
        if (userInfo == null){
            // 返回无权限
            response.setStatus(401);
            return false;
        }
        // 存到ThreadLocal中，保证每个请求的数据没有干扰，因为session域有可能
        UserHolder.saveUser(userInfo);
        return true;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
        HandlerInterceptor.super.postHandle(request, response, handler, modelAndView);
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        HandlerInterceptor.super.afterCompletion(request, response, handler, ex);
    }
}
