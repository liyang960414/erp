package com.sambound.erp.service.importing;

/**
 * Excel 导入服务配置类
 * 统一配置批量大小、并发数、事务超时等参数
 */
public class ImportServiceConfig {

    /**
     * 默认最大错误数量
     */
    public static final int DEFAULT_MAX_ERROR_COUNT = 1000;

    /**
     * 默认批量查询分片大小（避免 IN 查询参数过多）
     * PostgreSQL 通常限制为 32767
     */
    public static final int DEFAULT_BATCH_QUERY_CHUNK_SIZE = 1000;

    /**
     * 默认批量插入大小
     * 平衡性能和事务超时（每条记录约6个参数，1000条约6000个参数）
     */
    public static final int DEFAULT_BATCH_INSERT_SIZE = 1000;

    /**
     * 默认最大并发批次数量
     * 限制为连接池大小的一半（留一些连接给其他操作）
     * 连接池通常为20，所以设置为10个并发批次
     */
    public static final int DEFAULT_MAX_CONCURRENT_BATCHES = 10;

    /**
     * 默认事务超时时间（秒）
     */
    public static final int DEFAULT_TRANSACTION_TIMEOUT = 120;

    /**
     * 默认批次处理超时时间（分钟）
     */
    public static final int DEFAULT_BATCH_TIMEOUT_MINUTES = 30;

    /**
     * 采购订单批量大小
     */
    public static final int PURCHASE_ORDER_BATCH_SIZE = 100;

    /**
     * 采购订单事务超时时间（秒）
     */
    public static final int PURCHASE_ORDER_TRANSACTION_TIMEOUT = 1800;

    private ImportServiceConfig() {
        // 工具类，不允许实例化
    }
}


