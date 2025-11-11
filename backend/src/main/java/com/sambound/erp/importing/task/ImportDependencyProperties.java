package com.sambound.erp.importing.task;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 导入任务依赖及调度配置。
 */
@ConfigurationProperties(prefix = "erp.import")
public class ImportDependencyProperties {

    /**
     * 按导入类型定义依赖的前置类型列表。
     *
     * <pre>
     * dependencies:
     *   material:
     *     - unit
     *   bom:
     *     - material
     * </pre>
     */
    private Map<String, List<String>> dependencies = new HashMap<>();

    /**
     * 调度线程池配置。
     */
    private Scheduler scheduler = new Scheduler();

    /**
     * 每种导入类型允许的最大并发数。
     */
    private Map<String, Integer> typeConcurrency = new HashMap<>();

    public Map<String, List<String>> getDependencies() {
        return dependencies;
    }

    public void setDependencies(Map<String, List<String>> dependencies) {
        this.dependencies = dependencies != null ? dependencies : new HashMap<>();
    }

    public Scheduler getScheduler() {
        return scheduler;
    }

    public void setScheduler(Scheduler scheduler) {
        this.scheduler = scheduler != null ? scheduler : new Scheduler();
    }

    public Map<String, Integer> getTypeConcurrency() {
        return typeConcurrency;
    }

    public void setTypeConcurrency(Map<String, Integer> typeConcurrency) {
        this.typeConcurrency = typeConcurrency != null ? typeConcurrency : new HashMap<>();
    }

    public List<String> getDependenciesFor(String importType) {
        return dependencies.getOrDefault(importType, Collections.emptyList());
    }

    public int resolveConcurrencyLimit(String importType) {
        return typeConcurrency.getOrDefault(importType, scheduler.getMaxConcurrentTasks());
    }

    public static class Scheduler {
        /**
         * 调度线程池大小。
         */
        private int poolSize = 4;
        /**
         * 同时运行的最大任务数（全局）。
         */
        private int maxConcurrentTasks = 4;
        /**
         * 队列容量。
         */
        private int queueCapacity = 100;
        /**
         * 轮询待调度任务的时间间隔（毫秒）。
         */
        private long pollInterval = 2000L;

        public int getPoolSize() {
            return poolSize;
        }

        public void setPoolSize(int poolSize) {
            this.poolSize = poolSize;
        }

        public int getMaxConcurrentTasks() {
            return maxConcurrentTasks;
        }

        public void setMaxConcurrentTasks(int maxConcurrentTasks) {
            this.maxConcurrentTasks = maxConcurrentTasks;
        }

        public int getQueueCapacity() {
            return queueCapacity;
        }

        public void setQueueCapacity(int queueCapacity) {
            this.queueCapacity = queueCapacity;
        }

        public long getPollInterval() {
            return pollInterval;
        }

        public void setPollInterval(long pollInterval) {
            this.pollInterval = pollInterval;
        }
    }
}


