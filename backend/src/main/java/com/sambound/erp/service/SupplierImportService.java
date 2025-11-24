package com.sambound.erp.service;

import com.sambound.erp.dto.SupplierImportResponse;
import com.sambound.erp.repository.SupplierRepository;
import com.sambound.erp.service.importing.AbstractImportService;
import com.sambound.erp.service.importing.ImportContext;
import com.sambound.erp.service.importing.ImportProperties;
import com.sambound.erp.service.importing.ImportModuleConfig;
import com.sambound.erp.service.importing.supplier.SupplierImportProcessor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;

import java.io.InputStream;

@Service
public class SupplierImportService extends AbstractImportService<SupplierImportResponse> {

    private final SupplierRepository supplierRepository;
    private final ImportModuleConfig supplierModuleConfig;

    public SupplierImportService(
            SupplierRepository supplierRepository,
            PlatformTransactionManager transactionManager,
            ImportProperties importProperties) {
        super(transactionManager, importProperties.getModuleConfig("supplier"));
        this.supplierRepository = supplierRepository;
        this.supplierModuleConfig = importProperties.getModuleConfig("supplier");
    }

    @Override
    protected SupplierImportResponse importFromInputStream(InputStream inputStream, String fileName, long fileSize) throws Exception {
        SupplierImportProcessor processor = new SupplierImportProcessor(
                supplierRepository,
                transactionTemplate,
                executorService,
                supplierModuleConfig
        );
        return processor.process(inputStream);
    }

    @Override
    protected void logImportResult(SupplierImportResponse result, ImportContext context) {
        int total = result.supplierResult().totalRows();
        int success = result.supplierResult().successCount();
        int failure = result.supplierResult().failureCount();
        long duration = System.currentTimeMillis() - context.getStartTimeMillis();
        logger.info("[ExcelImport] stage=done module={} file={} total={} success={} failure={} durationMs={}",
                context.getModule(), context.getFileName(), total, success, failure, duration);
    }

    /**
     * 从字节数组执行导入（兼容旧代码）
     */
    public SupplierImportResponse importFromBytes(byte[] fileBytes, String fileName) {
        return importFromBytes(fileBytes, fileName, fileBytes.length);
    }
}

