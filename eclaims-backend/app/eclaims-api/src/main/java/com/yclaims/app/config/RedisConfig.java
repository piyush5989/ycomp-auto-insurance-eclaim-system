package com.yclaims.app.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Redis configuration with per-cache TTL policy.
 *
 * Cache key naming convention:
 *   claim:{claimId}                  - NOT cached (always live - status changes)
 *   policy:{policyNumber}            - 15 min (reference data)
 *   workshop:nearby:{zipCode}        - 30 min (changes infrequently)
 *   report:kpi:{region}              - 15 min (pre-aggregated snapshots)
 *   idempotency:{key}                - 24 hr (payment dedup)
 *   kafka:processed:{eventId}        - 24 hr (Kafka consumer dedup)
 *
 * What MUST NOT be cached:
 *   - Current claim status (customers see latest)
 *   - Payment amounts pending settlement
 *   - Audit log entries (append-only)
 *   - JWT validity (checked live against Keycloak)
 *   - Fraud flags (real-time decision)
 *
 * Three robustness behaviours that earlier caused production-style outages:
 *
 *   1. Default typing on a *dedicated* {@link ObjectMapper}: cached DTOs need
 *      {@code @class} metadata so they round-trip back to the right type.
 *      Without it, every cache hit explodes with
 *      {@code ClassCastException: LinkedHashMap cannot be cast to ...}.
 *
 *   2. The polymorphic-typing mapper is built as a private helper, NEVER as
 *      a {@code @Bean ObjectMapper}. Exposing it as a bean activates Spring
 *      Boot's {@code @ConditionalOnMissingBean} on the auto-configured
 *      primary {@link ObjectMapper}, which means the *application-wide*
 *      Jackson would inherit default typing too. That breaks every
 *      {@code RestTemplate} call - notably the Keycloak UMA decision call,
 *      which returns a plain {@code {"result":true}} that suddenly can't be
 *      parsed because Jackson expects {@code @class} at the root - causing
 *      every authz check to fail and every user to see empty data.
 *
 *   3. {@link CacheErrorHandler}: even with correct typing, a stale entry
 *      written by an older build, or a transient Redis outage, must never
 *      surface as a 500 to the user. The handler logs a warning, evicts the
 *      offending key, and lets the @Cacheable method recompute.
 */
@Slf4j
@Configuration
@EnableCaching
public class RedisConfig implements CachingConfigurer {

    /**
     * Builds the Jackson {@link ObjectMapper} used ONLY for serialising values
     * into Redis. Intentionally NOT a {@code @Bean} - exposing it would suppress
     * Spring Boot's primary {@link ObjectMapper} auto-configuration and turn
     * default typing on for every HTTP client and controller in the app, which
     * silently breaks {@link org.springframework.web.client.RestTemplate} JSON
     * parsing across the system.
     */
    private ObjectMapper buildRedisObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        PolymorphicTypeValidator ptv = BasicPolymorphicTypeValidator.builder()
                .allowIfBaseType(Object.class)
                .allowIfSubType("com.yclaims.")
                .allowIfSubType("java.util.")
                .allowIfSubType("java.time.")
                .allowIfSubType("java.math.")
                .allowIfSubType("java.lang.")
                .build();

        mapper.activateDefaultTyping(
                ptv,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY);

        return mapper;
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());

        GenericJackson2JsonRedisSerializer jsonSerializer =
                new GenericJackson2JsonRedisSerializer(buildRedisObjectMapper());
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);
        template.afterPropertiesSet();
        return template;
    }

    @Bean
    @Primary
    public CacheManager cacheManager(RedisConnectionFactory factory) {
        GenericJackson2JsonRedisSerializer jsonSerializer =
                new GenericJackson2JsonRedisSerializer(buildRedisObjectMapper());

        RedisCacheConfiguration defaults = RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(jsonSerializer));

        Map<String, RedisCacheConfiguration> cacheConfigs = new HashMap<>();
        cacheConfigs.put("policy", defaults.entryTtl(Duration.ofMinutes(15)));
        cacheConfigs.put("workshop", defaults.entryTtl(Duration.ofMinutes(30)));
        cacheConfigs.put("report", defaults.entryTtl(Duration.ofMinutes(15)));

        return RedisCacheManager.builder(factory)
                .cacheDefaults(defaults.entryTtl(Duration.ofMinutes(10)))
                .withInitialCacheConfigurations(cacheConfigs)
                .build();
    }

    /**
     * Wipes the Redis-backed application caches once on startup.
     *
     * Why this exists: a previous build wrote KPI report entries to Redis without
     * Jackson default-typing metadata ({@code @class}). After this config was
     * patched to enable default typing, those legacy entries still poisoned the
     * first request of every restart - they deserialised back to
     * {@link java.util.LinkedHashMap} and blew up the {@code @Cacheable} return
     * cast with {@link ClassCastException}. Clearing on startup costs at most
     * one cache miss per cache after deploy and prevents the entire class of
     * "old DTO shape stuck in Redis after deploy" outages.
     *
     * Auth decisions live in the in-memory {@code authzDecisions} cache and are
     * intentionally NOT cleared here.
     */
    @Bean
    public ApplicationRunner clearStaleRedisCachesOnStartup(CacheManager cacheManager) {
        return args -> {
            List<String> redisCaches = List.of("policy", "workshop", "report");
            for (String name : redisCaches) {
                Cache cache = cacheManager.getCache(name);
                if (cache == null) {
                    continue;
                }
                try {
                    cache.clear();
                    log.info("Cleared Redis cache '{}' on startup", name);
                } catch (RuntimeException ex) {
                    log.warn("Failed to clear Redis cache '{}' on startup: {} - "
                            + "stale entries (if any) will age out on TTL",
                            name, ex.getMessage());
                }
            }
        };
    }

    /**
     * Defence-in-depth: never let a Redis problem (connection blip, stale
     * entry from an older build, type drift after a deploy) propagate to the
     * caller as a 500. We log, evict the offending key, and let the
     * {@code @Cacheable} method recompute against the database.
     */
    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException ex, Cache cache, Object key) {
                log.warn("Redis GET failed for cache='{}' key='{}': {} - falling back to source",
                        cache.getName(), key, ex.getMessage());
                safeEvict(cache, key);
            }

            @Override
            public void handleCachePutError(RuntimeException ex, Cache cache, Object key, Object value) {
                log.warn("Redis PUT failed for cache='{}' key='{}': {} - response still served",
                        cache.getName(), key, ex.getMessage());
            }

            @Override
            public void handleCacheEvictError(RuntimeException ex, Cache cache, Object key) {
                log.warn("Redis EVICT failed for cache='{}' key='{}': {}",
                        cache.getName(), key, ex.getMessage());
            }

            @Override
            public void handleCacheClearError(RuntimeException ex, Cache cache) {
                log.warn("Redis CLEAR failed for cache='{}': {}", cache.getName(), ex.getMessage());
            }

            private void safeEvict(Cache cache, Object key) {
                try {
                    cache.evict(key);
                } catch (RuntimeException evictEx) {
                    log.debug("Best-effort evict also failed for cache='{}' key='{}': {}",
                            cache.getName(), key, evictEx.getMessage());
                }
            }
        };
    }
}
