package com.yclaims.app.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Virtual-thread executor — one thread per task, no pool contention for I/O-bound work. */
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
