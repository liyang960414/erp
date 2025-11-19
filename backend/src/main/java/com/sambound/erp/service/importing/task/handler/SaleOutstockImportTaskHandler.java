package com.sambound.erp.service.importing.task.handler;

import com.sambound.erp.dto.SaleOutstockImportResponse;
import com.sambound.erp.service.SaleOutstockImportService;
import com.sambound.erp.service.importing.ExcelImportService;
import com.sambound.erp.service.importing.task.ImportTaskExecutionResult;
import com.sambound.erp.service.importing.task.ImportTaskFailureDetail;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SaleOutstockImportTaskHandler extends AbstractImportTaskHandler<SaleOutstockImportResponse> {

    private final SaleOutstockImportService saleOutstockImportService;

    public SaleOutstockImportTaskHandler(SaleOutstockImportService saleOutstockImportService) {
        this.saleOutstockImportService = saleOutstockImportService;
    }

    @Override
    public String getImportType() {
        return "sale-outstock";
    }

    @Override
    protected ExcelImportService<SaleOutstockImportResponse> getImportService() {
        return saleOutstockImportService;
    }

    @Override
    protected ImportTaskExecutionResult convertToExecutionResult(SaleOutstockImportResponse response) {
        SaleOutstockImportResponse.SaleOutstockImportResult result = response.result();
        int total = safeInt(result.totalRows());
        int success = safeInt(result.successCount());
        int failure = safeInt(result.failureCount());
        List<ImportTaskFailureDetail> failures = result.errors() == null ? List.of()
                : result.errors().stream()
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
