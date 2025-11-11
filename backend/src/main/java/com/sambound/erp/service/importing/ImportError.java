package com.sambound.erp.service.importing;

import java.util.Objects;

/**
 * 导入错误信息，记录所在模块、行号、字段及详细描述。
 */
public final class ImportError {

    private final String section;
    private final int rowNumber;
    private final String field;
    private final String message;

    public ImportError(String section, int rowNumber, String field, String message) {
        this.section = section;
        this.rowNumber = rowNumber;
        this.field = field;
        this.message = message;
    }

    public String getSection() {
        return section;
    }

    public int getRowNumber() {
        return rowNumber;
    }

    public String getField() {
        return field;
    }

    public String getMessage() {
        return message;
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
                && Objects.equals(message, that.message);
    }

    @Override
    public int hashCode() {
        return Objects.hash(section, rowNumber, field, message);
    }

    @Override
    public String toString() {
        return "ImportError{"
                + "section='" + section + '\''
                + ", rowNumber=" + rowNumber
                + ", field='" + field + '\''
                + ", message='" + message + '\''
                + '}';
    }
}

