package com.sambound.erp.dto;

import java.util.List;

public record SaleOutstockImportResponse(
    SaleOutstockImportResult result
) {

    public record SaleOutstockImportResult(
        Integer totalRows,
        Integer successCount,
        Integer failureCount,
        List<ImportError> errors
    ) {}

    public record ImportError(
        String sheetName,
        Integer rowNumber,
        String field,
        String message
    ) {}
}

