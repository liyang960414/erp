package com.sambound.erp.service.importing;

import java.util.Collections;
import java.util.List;

/**
 * 基础导入摘要实现，封装常见统计字段。
 */
public class BasicImportSummary implements ImportSummary {

    private final int totalRows;
    private final int successCount;
    private final List<ImportError> errors;

    public BasicImportSummary(int totalRows, int successCount, List<ImportError> errors) {
        this.totalRows = totalRows;
        this.successCount = successCount;
        this.errors = errors == null ? Collections.emptyList() : Collections.unmodifiableList(errors);
    }

    @Override
    public int totalRows() {
        return totalRows;
    }

    @Override
    public int successCount() {
        return successCount;
    }

    @Override
    public List<ImportError> errors() {
        return errors;
    }
}

