package com.yclaims.app.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Async configuration using Java 21 Virtual Threads.
 * Virtual threads (Project Loom) allow near-Node.js concurrency without explicit reactive code.
 * A new virtual thread is spawned per task — no thread pool contention under load.
 *
 * Direct impact on 200M customer scale: the JVM can handle hundreds of thousands of
 * concurrent I/O-bound requests (DB, Redis, Kafka) without blocking OS threads.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean("claimsTaskExecutor")
    public TaskExecutor claimsTaskExecutor() {
        return new TaskExecutor() {
            private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

            @Override
            public void execute(Runnable task) {
                executor.submit(task);
            }
        };
    }
}
