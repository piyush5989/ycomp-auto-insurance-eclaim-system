package com.yclaims.app.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Caffeine (in-memory) cache for Keycloak authz decisions, isolated from the primary Redis
 * CacheManager so idempotency keys and report caches are not affected.
 */
@Configuration
public class AuthzCacheConfig {

    @Bean
    public CacheManager caffeineCacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager("authzDecisions");
        manager.setCaffeine(
                Caffeine.newBuilder()
                        .maximumSize(50_000)
                        .expireAfterWrite(5, TimeUnit.MINUTES)
        );
        return manager;
    }
}
