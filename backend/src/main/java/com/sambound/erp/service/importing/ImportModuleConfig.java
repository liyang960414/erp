package com.sambound.erp.service.importing;

import java.util.Objects;

/**
 * 单个导入模块的运行时配置，封装批量大小、并发数、超时等关键参数。
 */
public final class ImportModuleConfig {

    private final String module;
    private final int batchInsertSize;
    private final int maxConcurrentBatches;
    private final int batchTimeoutMinutes;
    private final int maxErrorCount;
    private final int transactionTimeoutSeconds;
    private final ExecutorType executorType;

    private ImportModuleConfig(Builder builder) {
        this.module = builder.module;
        this.batchInsertSize = builder.batchInsertSize;
        this.maxConcurrentBatches = builder.maxConcurrentBatches;
        this.batchTimeoutMinutes = builder.batchTimeoutMinutes;
        this.maxErrorCount = builder.maxErrorCount;
        this.transactionTimeoutSeconds = builder.transactionTimeoutSeconds;
        this.executorType = builder.executorType;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static ImportModuleConfig defaultConfig(String moduleName) {
        return builder()
                .module(moduleName)
                .batchInsertSize(ImportServiceConfig.DEFAULT_BATCH_INSERT_SIZE)
                .maxConcurrentBatches(ImportServiceConfig.DEFAULT_MAX_CONCURRENT_BATCHES)
                .batchTimeoutMinutes(ImportServiceConfig.DEFAULT_BATCH_TIMEOUT_MINUTES)
                .maxErrorCount(ImportServiceConfig.DEFAULT_MAX_ERROR_COUNT)
                .transactionTimeoutSeconds(ImportServiceConfig.DEFAULT_TRANSACTION_TIMEOUT)
                .executorType(ExecutorType.VIRTUAL_THREAD)
                .build();
    }

    public String module() {
        return module;
    }

    public int batchInsertSize() {
        return batchInsertSize;
    }

    public int maxConcurrentBatches() {
        return maxConcurrentBatches;
    }

    public int batchTimeoutMinutes() {
        return batchTimeoutMinutes;
    }

    public int maxErrorCount() {
        return maxErrorCount;
    }

    public int transactionTimeoutSeconds() {
        return transactionTimeoutSeconds;
    }

    public ExecutorType executorType() {
        return executorType;
    }

    public ImportModuleConfig withOverrides(ImportProperties.ModuleProperties properties) {
        if (properties == null) {
            return this;
        }
        return builder()
                .module(module)
                .batchInsertSize(firstNonNull(properties.getBatchInsertSize(), batchInsertSize))
                .maxConcurrentBatches(firstNonNull(properties.getMaxConcurrentBatches(), maxConcurrentBatches))
                .batchTimeoutMinutes(firstNonNull(properties.getBatchTimeoutMinutes(), batchTimeoutMinutes))
                .maxErrorCount(firstNonNull(properties.getMaxErrorCount(), maxErrorCount))
                .transactionTimeoutSeconds(firstNonNull(properties.getTransactionTimeoutSeconds(), transactionTimeoutSeconds))
                .executorType(properties.getExecutorType() != null ? properties.getExecutorType() : executorType)
                .build();
    }

    private static int firstNonNull(Integer value, int fallback) {
        return value != null ? value : fallback;
    }

    public enum ExecutorType {
        /**
         * 每个任务使用虚拟线程（Java 21）。
         */
        VIRTUAL_THREAD,
        /**
         * 使用固定大小线程池。
         */
        FIXED_THREAD_POOL
    }

    public static final class Builder {
        private String module;
        private int batchInsertSize = ImportServiceConfig.DEFAULT_BATCH_INSERT_SIZE;
        private int maxConcurrentBatches = ImportServiceConfig.DEFAULT_MAX_CONCURRENT_BATCHES;
        private int batchTimeoutMinutes = ImportServiceConfig.DEFAULT_BATCH_TIMEOUT_MINUTES;
        private int maxErrorCount = ImportServiceConfig.DEFAULT_MAX_ERROR_COUNT;
        private int transactionTimeoutSeconds = ImportServiceConfig.DEFAULT_TRANSACTION_TIMEOUT;
        private ExecutorType executorType = ExecutorType.VIRTUAL_THREAD;

        private Builder() {
        }

        public Builder module(String module) {
            this.module = module;
            return this;
        }

        public Builder batchInsertSize(int batchInsertSize) {
            this.batchInsertSize = batchInsertSize;
            return this;
        }

        public Builder maxConcurrentBatches(int maxConcurrentBatches) {
            this.maxConcurrentBatches = maxConcurrentBatches;
            return this;
        }

        public Builder batchTimeoutMinutes(int batchTimeoutMinutes) {
            this.batchTimeoutMinutes = batchTimeoutMinutes;
            return this;
        }

        public Builder maxErrorCount(int maxErrorCount) {
            this.maxErrorCount = maxErrorCount;
            return this;
        }

        public Builder transactionTimeoutSeconds(int transactionTimeoutSeconds) {
            this.transactionTimeoutSeconds = transactionTimeoutSeconds;
            return this;
        }

        public Builder executorType(ExecutorType executorType) {
            this.executorType = Objects.requireNonNull(executorType);
            return this;
        }

        public ImportModuleConfig build() {
            return new ImportModuleConfig(this);
        }
    }
}


