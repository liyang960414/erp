package com.sambound.erp.service.importing.task.handler;

import com.sambound.erp.dto.UnitImportResponse;
import com.sambound.erp.service.UnitImportService;
import com.sambound.erp.service.importing.ImportError;
import com.sambound.erp.service.importing.task.ImportTaskContext;
import com.sambound.erp.service.importing.task.ImportTaskExecutionResult;
import com.sambound.erp.service.importing.task.ImportTaskFailureDetail;
import com.sambound.erp.service.importing.task.ImportTaskHandler;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class UnitImportTaskHandler implements ImportTaskHandler {

    private final UnitImportService unitImportService;

    public UnitImportTaskHandler(UnitImportService unitImportService) {
        this.unitImportService = unitImportService;
    }

    @Override
    public String getImportType() {
        return "unit";
    }

    @Override
    public ImportTaskExecutionResult execute(ImportTaskContext context) {
        UnitImportResponse response = unitImportService.importFromBytes(
                context.fileContent(), context.fileName());
        List<ImportTaskFailureDetail> failures = response.errors() == null ? List.of()
                : response.errors().stream()
                .map(err -> new ImportTaskFailureDetail(
                        new ImportError(
                                "单位",
                                err.rowNumber() != null ? err.rowNumber() : 0,
                                err.field(),
                                err.message()),
                        null))
                .toList();
        return ImportTaskExecutionResult.builder()
                .totalCount(safeInt(response.totalRows()))
                .successCount(safeInt(response.successCount()))
                .failureCount(safeInt(response.failureCount()))
                .failures(failures)
                .summary(response)
                .build();
    }

    private int safeInt(Integer value) {
        return value != null ? value : 0;
    }
}


