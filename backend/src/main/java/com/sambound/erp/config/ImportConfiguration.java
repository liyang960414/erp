package com.sambound.erp.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Excel 导入系统统一配置管理。
 * 将原本分散在各处的硬编码配置值集中管理，提高可维护性和灵活性。
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "erp.import")
public class ImportConfiguration {

    /**
     * 错误收集配置
     */
    private ErrorConfig error = new ErrorConfig();

    /**
     * 批量操作配置
     */
    private BatchConfig batch = new BatchConfig();

    /**
     * 超时配置
     */
    private TimeoutConfig timeout = new TimeoutConfig();

    /**
     * 并发控制配置
     */
    private ConcurrencyConfig concurrency = new ConcurrencyConfig();

    @Data
    public static class ErrorConfig {
        /**
         * 单次导入任务的最大错误收集数量
         */
        private int maxErrorCount = 1000;
    }

    @Data
    public static class BatchConfig {
        /**
         * 批量查询时的分片大小，避免 IN 查询参数过多
         * PostgreSQL 通常限制为 32767
         */
        private int queryChunkSize = 1000;

        /**
         * 批量插入时的分片大小
         * 平衡性能和事务超时（每条记录约 6 个参数，1000 条约 6000 个参数）
         */
        private int insertSize = 1000;
    }

    @Data
    public static class TimeoutConfig {
        /**
         * 导入处理超时时间（分钟）
         */
        private int processingTimeoutMinutes = 30;

        /**
         * 事务超时时间（秒）
         */
        private int transactionTimeoutSeconds = 300;
    }

    @Data
    public static class ConcurrencyConfig {
        /**
         * 最大并发批次数量
         * 建议设置为数据库连接池大小的一半，留一些连接给其他操作
         * 连接池通常为 20，所以默认设置为 10 个并发批次
         */
        private int maxConcurrentBatches = 10;
    }
}
