package com.sambound.erp.dto;

import com.sambound.erp.service.importing.ImportError;
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
}

