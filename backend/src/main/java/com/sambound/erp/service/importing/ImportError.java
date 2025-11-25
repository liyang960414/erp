package com.sambound.erp.service.importing;

import lombok.Getter;

import java.util.Objects;

/**
 * 导入错误信息，记录所在模块、行号、字段及详细描述。
 */
@Getter
public final class ImportError {

    private final String section;
    private final int rowNumber;
    private final String field;
    private final String message;
    private final ErrorType errorType;
    private final String errorCode;

    public ImportError(String section, int rowNumber, String field, String message) {
        this(section, rowNumber, field, message, ErrorType.DATA_ERROR, null);
    }

    public ImportError(String section, int rowNumber, String field, String message, ErrorType errorType) {
        this(section, rowNumber, field, message, errorType, null);
    }

    public ImportError(String section, int rowNumber, String field, String message,
                       ErrorType errorType, String errorCode) {
        this.section = section;
        this.rowNumber = rowNumber;
        this.field = field;
        this.message = message;
        this.errorType = errorType == null ? ErrorType.DATA_ERROR : errorType;
        this.errorCode = errorCode;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ImportError that = (ImportError) o;
        return rowNumber == that.rowNumber
                && Objects.equals(section, that.section)
                && Objects.equals(field, that.field)
                && Objects.equals(message, that.message)
                && errorType == that.errorType
                && Objects.equals(errorCode, that.errorCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(section, rowNumber, field, message, errorType, errorCode);
    }

    @Override
    public String toString() {
        return "ImportError{"
                + "section='" + section + '\''
                + ", rowNumber=" + rowNumber
                + ", field='" + field + '\''
                + ", message='" + message + '\''
                + ", errorType='" + errorType + '\''
                + ", errorCode='" + errorCode + '\''
                + '}';
    }

    /**
     * 错误类型
     */
    public enum ErrorType {
        VALIDATION_ERROR,
        DATA_ERROR,
        SYSTEM_ERROR
    }
}

