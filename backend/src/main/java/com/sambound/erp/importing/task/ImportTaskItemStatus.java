package com.sambound.erp.importing.task;

/**
 * 导入任务子项状态。
 */
public enum ImportTaskItemStatus {
    /**
     * 等待执行。
     */
    PENDING,
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


