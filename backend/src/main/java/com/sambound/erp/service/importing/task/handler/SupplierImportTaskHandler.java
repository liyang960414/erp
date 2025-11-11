package com.sambound.erp.service.importing.task.handler;

import com.sambound.erp.dto.SupplierImportResponse;
import com.sambound.erp.service.SupplierImportService;
import com.sambound.erp.service.importing.ImportError;
import com.sambound.erp.service.importing.task.ImportTaskContext;
import com.sambound.erp.service.importing.task.ImportTaskExecutionResult;
import com.sambound.erp.service.importing.task.ImportTaskFailureDetail;
import com.sambound.erp.service.importing.task.ImportTaskHandler;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SupplierImportTaskHandler implements ImportTaskHandler {

    private final SupplierImportService supplierImportService;

    public SupplierImportTaskHandler(SupplierImportService supplierImportService) {
        this.supplierImportService = supplierImportService;
    }

    @Override
    public String getImportType() {
        return "supplier";
    }

    @Override
    public ImportTaskExecutionResult execute(ImportTaskContext context) {
        SupplierImportResponse response = supplierImportService.importFromBytes(
                context.fileContent(), context.fileName());
        SupplierImportResponse.SupplierImportResult result = response.supplierResult();
        int total = safeInt(result.totalRows());
        int success = safeInt(result.successCount());
        int failure = safeInt(result.failureCount());
        List<ImportTaskFailureDetail> failures = result.errors() == null ? List.of()
                : result.errors().stream()
                .map(err -> new ImportTaskFailureDetail(
                        new ImportError(
                                err.sheetName(),
                                err.rowNumber() != null ? err.rowNumber() : 0,
                                err.field(),
                                err.message()),
                        null))
                .toList();
        return ImportTaskExecutionResult.builder()
                .totalCount(total)
                .successCount(success)
                .failureCount(failure)
                .failures(failures)
                .summary(response)
                .build();
    }

    private int safeInt(Integer value) {
        return value != null ? value : 0;
    }
}


