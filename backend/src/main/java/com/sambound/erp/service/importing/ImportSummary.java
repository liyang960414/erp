package com.sambound.erp.service.importing;

import java.util.List;

/**
 * 导入摘要信息，描述某一类别数据的导入统计结果。
 */
public interface ImportSummary {

    /**
     * @return 总记录数
     */
    int totalRows();

    /**
     * @return 成功记录数
     */
    int successCount();

    /**
     * @return 失败记录数
     */
    default int failureCount() {
        return Math.max(0, totalRows() - successCount());
    }

    /**
     * @return 错误列表
     */
    List<ImportError> errors();
}

