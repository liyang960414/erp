package com.sambound.erp.service.importing.task.handler;

import com.sambound.erp.dto.UnitImportResponse;
import com.sambound.erp.service.UnitImportService;
import com.sambound.erp.service.importing.ExcelImportService;
import com.sambound.erp.service.importing.task.ImportTaskExecutionResult;
import com.sambound.erp.service.importing.task.ImportTaskFailureDetail;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class UnitImportTaskHandler extends AbstractImportTaskHandler<UnitImportResponse> {

    private final UnitImportService unitImportService;

    public UnitImportTaskHandler(UnitImportService unitImportService) {
        this.unitImportService = unitImportService;
    }

    @Override
    public String getImportType() {
        return "unit";
    }

    @Override
    protected ExcelImportService<UnitImportResponse> getImportService() {
        return unitImportService;
    }

    @Override
    protected ImportTaskExecutionResult convertToExecutionResult(UnitImportResponse response) {
        int total = safeInt(response.totalRows());
        int success = safeInt(response.successCount());
        int failure = safeInt(response.failureCount());
        List<ImportTaskFailureDetail> failures = response.errors() == null ? List.of()
                : response.errors().stream()
                .map(err -> new ImportTaskFailureDetail(err, null))
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
