package com.sambound.erp.service.importing;

/**
 * Excel 行校验器。
 *
 * @param <T> 校验对象类型
 */
@FunctionalInterface
public interface RowValidator<T> {

    /**
     * 校验行数据。
     *
     * @param value   需校验的对象
     * @param context 导入上下文
     * @param errors  错误收集器
     */
    void validate(T value, ImportContext context, ImportErrorCollector errors);
}

