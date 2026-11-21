package com.lxjn.blog.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.util.concurrent.TimeUnit;

@Component
public class RequestTimingInterceptor implements HandlerInterceptor {
    private static final Logger log = LoggerFactory.getLogger(RequestTimingInterceptor.class);
    private static final String START = RequestTimingInterceptor.class.getName() + ".start";

    /** 记录路由处理开始时刻，供同一请求完成后计算耗时。 */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (request.getAttribute(START) == null) request.setAttribute(START, System.nanoTime());
        return true;
    }

    /** 记录方法、路由模板、HTTP 状态和耗时，省略实际 URL 参数及请求内容。 */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception error) {
        if (request.getAttribute(START) instanceof Long start) {
            Object route = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
            log.info("{} {} status={} durationMs={} requestId={}", request.getMethod(), route,
                    response.getStatus(), TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start),
                    response.getHeader(RequestIdFilter.HEADER));
        }
    }
}
