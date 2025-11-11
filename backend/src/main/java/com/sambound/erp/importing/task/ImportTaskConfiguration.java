package com.sambound.erp.importing.task;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 导入任务调度相关配置。
 */
@Configuration
@EnableConfigurationProperties(ImportDependencyProperties.class)
public class ImportTaskConfiguration {

    @Bean(name = "importTaskExecutor")
    public Executor importTaskExecutor(ImportDependencyProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        int corePoolSize = properties.getScheduler().getPoolSize();
        int maxPoolSize = Math.max(corePoolSize, properties.getScheduler().getMaxConcurrentTasks());
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(properties.getScheduler().getQueueCapacity());
        executor.setThreadNamePrefix("import-task-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.initialize();
        return executor;
    }
}


