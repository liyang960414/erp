package com.sambound.erp.importing.task;

/**
 * 导入失败记录的处理状态。
 */
public enum ImportFailureStatus {
    /**
     * 初始失败，待处理。
     */
    PENDING,
    /**
     * 用户已提交修订，等待重新执行。
     */
    RESUBMITTED,
    /**
     * 已通过重试或人工修复处理完成。
     */
    RESOLVED
}


