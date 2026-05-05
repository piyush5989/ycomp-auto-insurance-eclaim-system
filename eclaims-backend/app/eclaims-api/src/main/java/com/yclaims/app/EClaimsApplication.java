package com.yclaims.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * eClaims API Application Entry Point.
 *
 * Assembles all 7 bounded-context modules into a single deployable JAR.
 * Java 21 Virtual Threads enabled in application.yml — near-Node.js concurrency
 * without explicit async code in most handlers.
 *
 * Microservice extraction path:
 *   Each module in /modules/* can be independently extracted to its own Spring Boot app.
 *   Zero domain code changes required — only adapter configuration changes.
 *   See: docs/extraction-guide.md
 *
 * Access points (local dev):
 *   API:           http://localhost:8090/api/v1
 *   Swagger UI:    http://localhost:8090/swagger-ui.html
 *   Liveness:      http://localhost:8090/actuator/health/liveness
 *   Readiness:     http://localhost:8090/actuator/health/readiness
 *   Metrics:       http://localhost:8090/actuator/prometheus
 */
@SpringBootApplication(scanBasePackages = "com.yclaims")
@EnableCaching
@EnableAsync
@EnableKafka
@EnableScheduling
public class EClaimsApplication {

    public static void main(String[] args) {
        SpringApplication.run(EClaimsApplication.class, args);
    }
}
