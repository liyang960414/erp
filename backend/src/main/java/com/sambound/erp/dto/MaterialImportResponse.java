package com.sambound.erp.dto;

import com.sambound.erp.service.importing.ImportError;
import java.util.List;

public record MaterialImportResponse(
    UnitGroupImportResult unitGroupResult,
    MaterialImportResult materialResult
) {
    public record UnitGroupImportResult(
        Integer totalRows,
        Integer successCount,
        Integer failureCount,
        List<ImportError> errors
    ) {}

    public record MaterialImportResult(
        Integer totalRows,
        Integer successCount,
        Integer failureCount,
        List<ImportError> errors
    ) {}
}

