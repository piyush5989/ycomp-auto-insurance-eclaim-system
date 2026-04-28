package com.yclaims.kernel.cache;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;

/**
 * Registers a custom Micrometer gauge measuring the Redis cache hit ratio.
 * Target: > 85% cache hit ratio. Alert threshold: < 75% (see prometheus-alerts.yml).
 *
 * Metric name: redis.cache.hit.ratio
 * Measured via: keyspace_hits / (keyspace_hits + keyspace_misses) from Redis INFO stats.
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class CacheMetricsConfig {

    private final RedisTemplate<String, Object> redisTemplate;

    @Bean
    public MeterBinder redisCacheHitRatioGauge(MeterRegistry registry) {
        return r -> r.gauge("redis.cache.hit.ratio", this, CacheMetricsConfig::computeHitRatio);
    }

    private double computeHitRatio() {
        try {
            var info = redisTemplate.getConnectionFactory();
            if (info == null) return 0.0;
            // Redis INFO stats — keyspace_hits and keyspace_misses
            var props = redisTemplate.getConnectionFactory()
                    .getConnection().serverCommands().info("stats");
            if (props == null) return 0.0;

            long hits = Long.parseLong(props.getProperty("keyspace_hits", "0"));
            long misses = Long.parseLong(props.getProperty("keyspace_misses", "0"));
            long total = hits + misses;
            return total == 0 ? 1.0 : (double) hits / total;
        } catch (Exception e) {
            log.warn("Unable to compute Redis cache hit ratio: {}", e.getMessage());
            return 0.0;
        }
    }
}
