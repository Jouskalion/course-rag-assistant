package com.courseqa.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ExecutorsConfig {
    @Bean(destroyMethod = "shutdownNow")
    public ExecutorService qaWorkers() {
        return Executors.newFixedThreadPool(2, Thread.ofPlatform().name("qa-worker-", 1).factory());
    }

    @Bean(destroyMethod = "shutdownNow")
    public ScheduledExecutorService qaDeadlines() {
        var timer = new ScheduledThreadPoolExecutor(2, Thread.ofPlatform().name("qa-deadline-", 1).factory());
        timer.setRemoveOnCancelPolicy(true);
        timer.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        return timer;
    }
}
