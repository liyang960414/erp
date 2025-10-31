package com.sambound.erp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * 异步任务配置
 * 使用Java 25的Virtual Threads实现真正的异步执行
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * 虚拟线程执行器
     * 用于审计日志等I/O密集型任务
     */
    @Bean(name = "auditLogExecutor")
    public Executor auditLogExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}

