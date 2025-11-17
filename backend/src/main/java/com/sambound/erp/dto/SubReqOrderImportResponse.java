package com.sambound.erp.dto;

import com.sambound.erp.service.importing.ImportError;
import java.util.List;

public record SubReqOrderImportResponse(
    SubReqOrderImportResult subReqOrderResult
) {
    public record SubReqOrderImportResult(
        Integer totalRows,
        Integer successCount,
        Integer failureCount,
        List<ImportError> errors
    ) {}
}

