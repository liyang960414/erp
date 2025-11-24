package com.sambound.erp.service;

import com.sambound.erp.dto.SupplierImportResponse;
import com.sambound.erp.repository.SupplierRepository;
import com.sambound.erp.service.importing.AbstractImportService;
import com.sambound.erp.service.importing.supplier.SupplierImportProcessor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;

import java.io.InputStream;

@Service
public class SupplierImportService extends AbstractImportService<SupplierImportResponse> {

    private final SupplierRepository supplierRepository;

    public SupplierImportService(
            SupplierRepository supplierRepository,
            PlatformTransactionManager transactionManager) {
        super(transactionManager);
        this.supplierRepository = supplierRepository;
    }

    @Override
    protected SupplierImportResponse importFromInputStream(InputStream inputStream, String fileName, long fileSize) throws Exception {
        SupplierImportProcessor processor = new SupplierImportProcessor(
                supplierRepository,
                transactionTemplate,
                executorService
        );
        return processor.process(inputStream);
    }

    @Override
    protected void logImportResult(SupplierImportResponse result) {
        logger.info("供应商导入完成：总计 {} 条，成功 {} 条，失败 {} 条",
                result.supplierResult().totalRows(),
                result.supplierResult().successCount(),
                result.supplierResult().failureCount());
    }

    /**
     * 从字节数组执行导入（兼容旧代码）
     */
    public SupplierImportResponse importFromBytes(byte[] fileBytes, String fileName) {
        return importFromBytes(fileBytes, fileName, fileBytes.length);
    }
}

