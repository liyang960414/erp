package com.sambound.erp.service.importing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 导入错误收集器，支持最大错误数量限制。
 */
public class ImportErrorCollector {

    private final int maxErrorCount;
    private final List<ImportError> errors = new ArrayList<>();

    public ImportErrorCollector(int maxErrorCount) {
        this.maxErrorCount = maxErrorCount;
    }

    public boolean addError(String section, int rowNumber, String field, String message) {
        if (errors.size() >= maxErrorCount) {
            return false;
        }
        errors.add(new ImportError(section, rowNumber, field, message));
        return true;
    }

    public List<ImportError> getErrors() {
        return Collections.unmodifiableList(errors);
    }

    public boolean isLimitReached() {
        return errors.size() >= maxErrorCount;
    }

    public int size() {
        return errors.size();
    }
}

