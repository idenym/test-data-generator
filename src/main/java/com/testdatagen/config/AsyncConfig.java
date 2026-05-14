package com.testdatagen.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * 数据生成专用线程池 - 用于并发调用大模型
     * IO密集型任务，线程数 = CPU核心数 * 2
     */
    @Bean("dataGenExecutor")
    public Executor dataGenExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        int cpuCores = Runtime.getRuntime().availableProcessors();
        executor.setCorePoolSize(Math.max(cpuCores * 2, 8));  // 至少8个核心线程
        executor.setMaxPoolSize(Math.max(cpuCores * 4, 16));  // 至少16个最大线程
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("datagen-");
        executor.setKeepAliveSeconds(60);
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
