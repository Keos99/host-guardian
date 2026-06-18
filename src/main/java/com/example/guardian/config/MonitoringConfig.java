package com.example.guardian.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Beans backing the parallel execution of monitoring cycles.
 */
@Configuration
public class MonitoringConfig {

    /**
     * Creates the bounded executor used to check hosts in parallel.
     *
     * <p>The pool size comes from {@code monitor.concurrency}. Threads are daemon
     * threads so the pool never blocks JVM shutdown, and the executor is closed
     * automatically through its {@code shutdown} destroy method.
     *
     * @param monitorProperties global monitoring settings
     * @return fixed-size executor for host checks
     */
    @Bean(destroyMethod = "shutdown")
    public ExecutorService monitoringExecutor(MonitorProperties monitorProperties) {
        int size = Math.max(1, monitorProperties.getConcurrency());
        return Executors.newFixedThreadPool(size, new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger(1);

            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "host-monitor-" + counter.getAndIncrement());
                thread.setDaemon(true);
                return thread;
            }
        });
    }
}
