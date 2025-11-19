package com.sambound.erp.service.importing.task.handler;

import com.sambound.erp.dto.PurchaseOrderImportResponse;
import com.sambound.erp.service.PurchaseOrderImportService;
import com.sambound.erp.service.importing.ExcelImportService;
import com.sambound.erp.service.importing.task.ImportTaskExecutionResult;
import com.sambound.erp.service.importing.task.ImportTaskFailureDetail;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PurchaseOrderImportTaskHandler extends AbstractImportTaskHandler<PurchaseOrderImportResponse> {

    private final PurchaseOrderImportService purchaseOrderImportService;

    public PurchaseOrderImportTaskHandler(PurchaseOrderImportService purchaseOrderImportService) {
        this.purchaseOrderImportService = purchaseOrderImportService;
    }

    @Override
    public String getImportType() {
        return "purchase-order";
    }

    @Override
    protected ExcelImportService<PurchaseOrderImportResponse> getImportService() {
        return purchaseOrderImportService;
    }

    @Override
    protected ImportTaskExecutionResult convertToExecutionResult(PurchaseOrderImportResponse response) {
        PurchaseOrderImportResponse.PurchaseOrderImportResult result = response.purchaseOrderResult();
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
