package com.sambound.erp.service.importing.task.handler;

import com.sambound.erp.dto.SaleOrderImportResponse;
import com.sambound.erp.service.SaleOrderImportService;
import com.sambound.erp.service.importing.ExcelImportService;
import com.sambound.erp.service.importing.task.ImportTaskExecutionResult;
import com.sambound.erp.service.importing.task.ImportTaskFailureDetail;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SaleOrderImportTaskHandler extends AbstractImportTaskHandler<SaleOrderImportResponse> {

    private final SaleOrderImportService saleOrderImportService;

    public SaleOrderImportTaskHandler(SaleOrderImportService saleOrderImportService) {
        this.saleOrderImportService = saleOrderImportService;
    }

    @Override
    public String getImportType() {
        return "sale-order";
    }

    @Override
    protected ExcelImportService<SaleOrderImportResponse> getImportService() {
        return saleOrderImportService;
    }

    @Override
    protected ImportTaskExecutionResult convertToExecutionResult(SaleOrderImportResponse response) {
        SaleOrderImportResponse.SaleOrderImportResult result = response.saleOrderResult();
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
