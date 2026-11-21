package com.lxjn.blog.config;

import com.lxjn.blog.web.DatabaseReadinessInterceptor;
import com.lxjn.blog.web.RequestIdFilter;
import com.lxjn.blog.web.RequestTimingInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

@Configuration(proxyBeanMethods = false)
public class WebConfiguration implements WebMvcConfigurer {
    private final RequestTimingInterceptor timing;
    private final DatabaseReadinessInterceptor readiness;
    private final String[] allowedOrigins;

    /** 读取跨域来源白名单，忽略空白项，并禁止凭据模式下的通配来源。 */
    public WebConfiguration(
            RequestTimingInterceptor timing,
            DatabaseReadinessInterceptor readiness,
            @Value("${BLOG_CORS_ALLOWED_ORIGINS:}") String origins) {
        this.timing = timing;
        this.readiness = readiness;
        this.allowedOrigins = Arrays.stream(origins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .distinct()
                .toArray(String[]::new);
        if (Arrays.asList(allowedOrigins).contains("*")) {
            throw new IllegalArgumentException("BLOG_CORS_ALLOWED_ORIGINS 必须列出具体来源");
        }
    }

    /** 记录请求耗时，并在访问管理业务前检查数据库是否就绪。 */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(timing);
        registry.addInterceptor(readiness).excludePathPatterns("/setup/**", "/health", "/actuator/**", "/error");
    }

    /** 向白名单来源开放管理接口，并允许前端发送凭据和读取请求 ID。 */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        if (allowedOrigins.length == 0) return;
        registry.addMapping("/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("Authorization", "Content-Type", "Accept", "Accept-Language")
                .exposedHeaders(RequestIdFilter.HEADER)
                .allowCredentials(true)
                .maxAge(1800);
    }
}
