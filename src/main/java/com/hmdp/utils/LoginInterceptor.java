package com.hmdp.utils;

import org.springframework.web.servlet.HandlerInterceptor;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;



public class LoginInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)  {
        // 判断是否需要拦截 （ThreadLocal中是否含有用户）
        if(UserHolder.getUser() == null){
            response.setStatus(401);
            return false;
        }

        // 有用户则放行
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex){
        //移除用户
        UserHolder.removeUser();
    }
}
