package com.wecreate.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
public class ThreadPoolConfig {

    /**
     * 全局线程池配置
     * 用于处理异步任务，如CLI请求处理
     *
     * @return Executor
     */
    @Bean(name = "globalTaskExecutor")
    public Executor globalTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 核心线程数 - 提高到20以增强并发处理能力
        executor.setCorePoolSize(20);
        // 最大线程数 - 提高到50以应对高峰期负载
        executor.setMaxPoolSize(50);
        // 队列容量 - 增加到200以缓冲更多任务
        executor.setQueueCapacity(200);
        // 线程名前缀
        executor.setThreadNamePrefix("Global-Async-");
        // 线程池关闭时等待所有任务完成
        executor.setWaitForTasksToCompleteOnShutdown(true);
        // 等待时间
        executor.setAwaitTerminationSeconds(30);
        // 拒绝策略：由调用线程处理该任务
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}