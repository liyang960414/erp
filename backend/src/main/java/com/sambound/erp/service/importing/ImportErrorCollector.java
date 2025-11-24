package com.sambound.erp.service.importing;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 导入错误收集器，支持最大错误数量限制。
 * 增强功能：错误分类、错误统计
 */
public class ImportErrorCollector {

    private final int maxErrorCount;
    private final List<ImportError> errors = new ArrayList<>();

    public ImportErrorCollector(int maxErrorCount) {
        this.maxErrorCount = maxErrorCount;
    }

    public ImportErrorCollector() {
        this(ImportServiceConfig.DEFAULT_MAX_ERROR_COUNT);
    }

    /**
     * 添加错误
     *
     * @param section   模块名称
     * @param rowNumber 行号
     * @param field     字段名
     * @param message   错误消息
     * @return 是否成功添加
     */
    public boolean addError(String section, int rowNumber, String field, String message) {
        return addError(section, rowNumber, field, message, ImportError.ErrorType.DATA_ERROR, null);
    }

    /**
     * 添加错误（带错误类型）
     *
     * @param section   模块名称
     * @param rowNumber 行号
     * @param field     字段名
     * @param message   错误消息
     * @param errorType 错误类型
     * @return 是否成功添加
     */
    public boolean addError(String section, int rowNumber, String field, String message,
                            ImportError.ErrorType errorType) {
        return addError(section, rowNumber, field, message, errorType, null);
    }

    /**
     * 添加错误（带错误类型与错误码）
     */
    public boolean addError(String section, int rowNumber, String field, String message,
                            ImportError.ErrorType errorType, String errorCode) {
        if (errors.size() >= maxErrorCount) {
            return false;
        }
        errors.add(new ImportError(section, rowNumber, field, message, errorType, errorCode));
        return true;
    }

    /**
     * 添加验证错误
     */
    public boolean addValidationError(String section, int rowNumber, String field, String message) {
        return addError(section, rowNumber, field, message, ImportError.ErrorType.VALIDATION_ERROR);
    }

    /**
     * 添加数据错误
     */
    public boolean addDataError(String section, int rowNumber, String field, String message) {
        return addError(section, rowNumber, field, message, ImportError.ErrorType.DATA_ERROR);
    }

    /**
     * 添加系统错误
     */
    public boolean addSystemError(String section, int rowNumber, String field, String message) {
        return addError(section, rowNumber, field, message, ImportError.ErrorType.SYSTEM_ERROR);
    }

    /**
     * 获取所有错误
     */
    public List<ImportError> getErrors() {
        return Collections.unmodifiableList(errors);
    }

    /**
     * 按模块分组获取错误
     */
    public Map<String, List<ImportError>> getErrorsBySection() {
        return errors.stream()
                .collect(Collectors.groupingBy(ImportError::getSection));
    }

    /**
     * 按字段分组获取错误
     */
    public Map<String, List<ImportError>> getErrorsByField() {
        return errors.stream()
                .filter(error -> error.getField() != null)
                .collect(Collectors.groupingBy(ImportError::getField));
    }

    /**
     * 获取错误统计信息
     */
    public ErrorStatistics getStatistics() {
        Map<String, Long> errorsBySection = errors.stream()
                .collect(Collectors.groupingBy(ImportError::getSection, Collectors.counting()));

        Map<String, Long> errorsByField = errors.stream()
                .filter(error -> error.getField() != null)
                .collect(Collectors.groupingBy(ImportError::getField, Collectors.counting()));

        return new ErrorStatistics(errors.size(), errorsBySection, errorsByField);
    }

    /**
     * 是否达到错误数量限制
     */
    public boolean isLimitReached() {
        return errors.size() >= maxErrorCount;
    }

    /**
     * 错误数量
     */
    public int size() {
        return errors.size();
    }

    /**
     * 错误统计信息
     */
    public static class ErrorStatistics {
        private final int totalErrors;
        private final Map<String, Long> errorsBySection;
        private final Map<String, Long> errorsByField;

        public ErrorStatistics(int totalErrors,
                               Map<String, Long> errorsBySection,
                               Map<String, Long> errorsByField) {
            this.totalErrors = totalErrors;
            this.errorsBySection = errorsBySection;
            this.errorsByField = errorsByField;
        }

        public int getTotalErrors() {
            return totalErrors;
        }

        public Map<String, Long> getErrorsBySection() {
            return errorsBySection;
        }

        public Map<String, Long> getErrorsByField() {
            return errorsByField;
        }
    }
}

