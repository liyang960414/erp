package com.sambound.erp.service.importing.task.handler;

import com.sambound.erp.service.importing.ExcelImportService;
import com.sambound.erp.service.importing.task.ImportTaskContext;
import com.sambound.erp.service.importing.task.ImportTaskExecutionResult;
import com.sambound.erp.service.importing.task.ImportTaskHandler;

/**
 * 通用导入任务处理器基类。
 *
 * @param <R> 导入服务返回的结果类型
 */
public abstract class AbstractImportTaskHandler<R> implements ImportTaskHandler {

    /**
     * 获取具体的导入服务实例。
     */
    protected abstract ExcelImportService<R> getImportService();

    /**
     * 将导入结果转换为任务执行结果。
     */
    protected abstract ImportTaskExecutionResult convertToExecutionResult(R response);

    @Override
    public ImportTaskExecutionResult execute(ImportTaskContext context) throws Exception {
        R response = getImportService().importFromBytes(
                context.fileContent(),
                context.fileName()
        );
        return convertToExecutionResult(response);
    }
}
