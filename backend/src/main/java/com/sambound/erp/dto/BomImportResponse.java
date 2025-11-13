package com.sambound.erp.dto;

import java.util.List;

public record BomImportResponse(
        BomImportResult bomResult,
        BomItemImportResult itemResult
) {
    public record BomImportResult(
            Integer totalRows,
            Integer successCount,
            Integer failureCount,
            List<ImportError> errors
    ) {
    }

    public record BomItemImportResult(
            Integer totalRows,
            Integer successCount,
            Integer failureCount,
            List<ImportError> errors
    ) {
    }

    public record ImportError(
            String sheetName,
            Integer rowNumber,
            String field,
            String message
    ) {
    }
}

