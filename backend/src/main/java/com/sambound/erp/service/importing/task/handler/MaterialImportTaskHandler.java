package com.sambound.erp.service.importing.task.handler;

import com.sambound.erp.dto.MaterialImportResponse;
import com.sambound.erp.service.MaterialImportService;
import com.sambound.erp.service.importing.task.ImportTaskContext;
import com.sambound.erp.service.importing.task.ImportTaskExecutionResult;
import com.sambound.erp.service.importing.task.ImportTaskFailureDetail;
import com.sambound.erp.service.importing.task.ImportTaskHandler;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class MaterialImportTaskHandler implements ImportTaskHandler {

    private final MaterialImportService materialImportService;

    public MaterialImportTaskHandler(MaterialImportService materialImportService) {
        this.materialImportService = materialImportService;
    }

    @Override
    public String getImportType() {
        return "material";
    }

    @Override
    public ImportTaskExecutionResult execute(ImportTaskContext context) {
        MaterialImportResponse response = materialImportService.importFromBytes(
                context.fileContent(), context.fileName());
        MaterialImportResponse.UnitGroupImportResult groupResult = response.unitGroupResult();
        MaterialImportResponse.MaterialImportResult materialResult = response.materialResult();

        int total = safeInt(groupResult.totalRows()) + safeInt(materialResult.totalRows());
        int success = safeInt(groupResult.successCount()) + safeInt(materialResult.successCount());
        int failure = safeInt(groupResult.failureCount()) + safeInt(materialResult.failureCount());

        List<ImportTaskFailureDetail> failures = new ArrayList<>();
        if (groupResult.errors() != null) {
            groupResult.errors().forEach(err -> failures.add(new ImportTaskFailureDetail(err, null)));
        }
        if (materialResult.errors() != null) {
            materialResult.errors().forEach(err -> failures.add(new ImportTaskFailureDetail(err, null)));
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


