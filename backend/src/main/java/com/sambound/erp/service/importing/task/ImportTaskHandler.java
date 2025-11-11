package com.sambound.erp.service.importing.task;

/**
 * 导入任务执行器接口。
 */
public interface ImportTaskHandler {

    /**
     * 该执行器支持的导入类型。
     *
     * @return 导入类型标识（需与前端提交一致）
     */
    String getImportType();

    /**
     * 执行导入。
     *
     * @param context 任务上下文
     * @return 执行结果
     */
    ImportTaskExecutionResult execute(ImportTaskContext context) throws Exception;
}


