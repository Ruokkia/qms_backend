package com.kangli.qms.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步任务线程池配置。
 *
 * <p>供异常自动触发事件监听（{@code ExceptionTriggerListener}）使用，
 * 使检验业务线程在发布不合格事件后立即返回，派单在独立线程异步执行，
 * 实现「实时触发 + 检验/异常生成解耦」。</p>
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /** 异常自动触发监听器专用线程池 */
    @Bean("exceptionTriggerExecutor")
    public Executor exceptionTriggerExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("exc-trigger-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
