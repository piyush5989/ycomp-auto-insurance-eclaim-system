package com.yclaims.app.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Dedicated in-memory (Caffeine) cache for Keycloak authorization decisions.
 *
 * Named "caffeineCacheManager" so it can be selected explicitly via
 * @Cacheable(cacheManager = "caffeineCacheManager") — leaving the default
 * Redis CacheManager undisturbed for other caches (idempotency keys, etc.).
 *
 * Cache entry lifetime: 5 minutes.
 * This means a policy change in Keycloak Admin UI propagates within ≤5 minutes
 * with no application restart required.
 *
 * Max entries: 50,000 (covers 50k concurrent user×permission combinations
 * comfortably within normal JVM heap).
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
