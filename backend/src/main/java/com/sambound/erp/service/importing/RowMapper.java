package com.sambound.erp.service.importing;

import cn.idev.excel.context.AnalysisContext;

/**
 * Excel 行映射器，用于将 Excel 行数据转换为领域对象。
 *
 * @param <S> 原始 Excel 行绑定对象
 * @param <T> 目标领域对象
 */
@FunctionalInterface
public interface RowMapper<S, T> {

    /**
     * 将 Excel 行数据转换为目标对象。
     *
     * @param source  Excel 行绑定对象
     * @param context Excel 解析上下文
     * @return 转换后的目标对象
     */
    T map(S source, AnalysisContext context);
}

