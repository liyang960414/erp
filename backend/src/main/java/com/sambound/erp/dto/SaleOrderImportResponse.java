package com.sambound.erp.dto;

import java.util.List;

public record SaleOrderImportResponse(
    SaleOrderImportResult saleOrderResult
) {
    public record SaleOrderImportResult(
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
