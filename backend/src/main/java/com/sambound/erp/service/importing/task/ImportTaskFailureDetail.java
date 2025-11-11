package com.sambound.erp.service.importing.task;

import com.sambound.erp.service.importing.ImportError;

/**
 * 失败行详细信息。
 *
 * @param error 错误描述
 * @param rawPayload 原始行数据（JSON等）
 */
public record ImportTaskFailureDetail(ImportError error, String rawPayload) {
}


