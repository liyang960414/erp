package com.sambound.erp.importing.task;

/**
 * 导入任务状态。
 */
public enum ImportTaskStatus {
    /**
     * 等待依赖完成。
     */
    WAITING,
    /**
     * 已在队列中等待执行。
     */
    QUEUED,
    /**
     * 正在执行。
     */
    RUNNING,
    /**
     * 已完成。
     */
    COMPLETED,
    /**
     * 执行失败。
     */
    FAILED,
    /**
     * 已取消。
     */
    CANCELLED
}


