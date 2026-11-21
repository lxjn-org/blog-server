package com.lxjn.blog.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
@ConditionalOnProperty(name = "spring.cache.type", havingValue = "redis")
public class RedisCacheConfiguration implements CachingConfigurer {
    private static final Logger log = LoggerFactory.getLogger(RedisCacheConfiguration.class);

    /** 缓存异常时继续执行关系数据库业务，并记录失败的缓存操作。 */
    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            /** 将读缓存失败视为缓存未命中。 */
            @Override
            public void handleCacheGetError(RuntimeException error, Cache cache, Object key) {
                warn("read", cache, error);
            }

            /** 保留业务查询结果，缓存写入失败不影响响应。 */
            @Override
            public void handleCachePutError(RuntimeException error, Cache cache, Object key, Object value) {
                warn("write", cache, error);
            }

            /** 记录单条缓存失效失败，待缓存过期后重新加载。 */
            @Override
            public void handleCacheEvictError(RuntimeException error, Cache cache, Object key) {
                warn("evict", cache, error);
            }

            /** 记录分组缓存失效失败。 */
            @Override
            public void handleCacheClearError(RuntimeException error, Cache cache) {
                warn("clear", cache, error);
            }
        };
    }

    /** 记录缓存名和异常类型，省略缓存键和值。 */
    private static void warn(String operation, Cache cache, RuntimeException error) {
        log.warn("Redis cache {} failed for {}: {}", operation, cache.getName(), error.getClass().getSimpleName());
    }
}
