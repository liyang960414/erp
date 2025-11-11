package com.sambound.erp.service.importing.task.handler;

import com.sambound.erp.dto.BomImportResponse;
import com.sambound.erp.service.BomImportService;
import com.sambound.erp.service.importing.ImportError;
import com.sambound.erp.service.importing.task.ImportTaskContext;
import com.sambound.erp.service.importing.task.ImportTaskExecutionResult;
import com.sambound.erp.service.importing.task.ImportTaskFailureDetail;
import com.sambound.erp.service.importing.task.ImportTaskHandler;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class BomImportTaskHandler implements ImportTaskHandler {

    private final BomImportService bomImportService;

    public BomImportTaskHandler(BomImportService bomImportService) {
        this.bomImportService = bomImportService;
    }

    @Override
    public String getImportType() {
        return "bom";
    }

    @Override
    public ImportTaskExecutionResult execute(ImportTaskContext context) {
        BomImportResponse response = bomImportService.importFromBytes(
                context.fileContent(), context.fileName());
        BomImportResponse.BomImportResult bomResult = response.bomResult();
        BomImportResponse.BomItemImportResult itemResult = response.itemResult();

        int total = safeInt(bomResult.totalRows()) + safeInt(itemResult.totalRows());
        int success = safeInt(bomResult.successCount()) + safeInt(itemResult.successCount());
        int failure = safeInt(bomResult.failureCount()) + safeInt(itemResult.failureCount());

        List<ImportTaskFailureDetail> failures = new ArrayList<>();
        if (bomResult.errors() != null) {
            bomResult.errors().forEach(err -> failures.add(new ImportTaskFailureDetail(
                    new ImportError(
                            err.sheetName(),
                            err.rowNumber() != null ? err.rowNumber() : 0,
                            err.field(),
                            err.message()),
                    null)));
        }
        if (itemResult.errors() != null) {
            itemResult.errors().forEach(err -> failures.add(new ImportTaskFailureDetail(
                    new ImportError(
                            err.sheetName(),
                            err.rowNumber() != null ? err.rowNumber() : 0,
                            err.field(),
                            err.message()),
                    null)));
        }

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


