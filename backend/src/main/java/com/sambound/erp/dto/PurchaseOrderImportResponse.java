package com.sambound.erp.dto;

import java.util.List;

public record PurchaseOrderImportResponse(
    PurchaseOrderImportResult purchaseOrderResult
) {
    public record PurchaseOrderImportResult(
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

