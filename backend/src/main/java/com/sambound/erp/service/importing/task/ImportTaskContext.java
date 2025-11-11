package com.sambound.erp.service.importing.task;

/**
 * 导入任务执行上下文。
 */
public record ImportTaskContext(
        String taskCode,
        String importType,
        byte[] fileContent,
        String fileName,
        String contentType,
        String optionsJson
) {
}


