package com.sambound.erp.dto;

import com.sambound.erp.service.importing.ImportError;
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
}

