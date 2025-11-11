package com.sambound.erp.dto;

import com.sambound.erp.service.importing.ImportError;
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
}
