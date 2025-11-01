package com.sambound.erp.dto;

import java.util.List;

public record UnitImportResponse(
    Integer totalRows,
    Integer successCount,
    Integer failureCount,
    List<ImportError> errors
) {
    public record ImportError(
        Integer rowNumber,
        String field,
        String message
    ) {}
}

