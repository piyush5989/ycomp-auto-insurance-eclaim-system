package com.yclaims.app.config;

import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Redis configuration with per-cache TTL policy.
 *
 * Cache key naming convention:
 *   claim:{claimId}                  ← NOT cached (always live — status changes)
 *   policy:{policyNumber}            ← 15 min (reference data)
 *   workshop:nearby:{zipCode}        ← 30 min (changes infrequently)
 *   report:kpi:{region}              ← 15 min (pre-aggregated snapshots)
 *   idempotency:{key}                ← 24 hr (payment dedup)
 *   kafka:processed:{eventId}        ← 24 hr (Kafka consumer dedup)
 *
 * What MUST NOT be cached:
 *   - Current claim status (customers see latest)
 *   - Payment amounts pending settlement
 *   - Audit log entries (append-only)
 *   - JWT validity (checked live against Keycloak)
 *   - Fraud flags (real-time decision)
 */
@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory factory) {
        RedisCacheConfiguration defaults = RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer()));

        Map<String, RedisCacheConfiguration> cacheConfigs = new HashMap<>();

        // Policy reference data — 15 min TTL
        cacheConfigs.put("policy", defaults.entryTtl(Duration.ofMinutes(15)));

        // Workshop search by location — 30 min TTL
        cacheConfigs.put("workshop", defaults.entryTtl(Duration.ofMinutes(30)));

        // KPI report snapshots — 15 min TTL
        cacheConfigs.put("report", defaults.entryTtl(Duration.ofMinutes(15)));

        return RedisCacheManager.builder(factory)
                .cacheDefaults(defaults.entryTtl(Duration.ofMinutes(10)))
                .withInitialCacheConfigurations(cacheConfigs)
                .build();
    }
}
