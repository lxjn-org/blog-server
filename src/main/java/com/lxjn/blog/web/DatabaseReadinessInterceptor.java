package com.lxjn.blog.web;

import com.lxjn.blog.system.DatabaseReadiness;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class DatabaseReadinessInterceptor implements HandlerInterceptor {
    private final DatabaseReadiness database;

    /** 复用健康接口的数据库检查规则。 */
    public DatabaseReadinessInterceptor(DatabaseReadiness database) {
        this.database = database;
    }

    /** 在数据库就绪前阻止管理业务请求，放行浏览器预检。 */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!"OPTIONS".equals(request.getMethod())) database.requireReady();
        return true;
    }
}
