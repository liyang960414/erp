package com.sambound.erp.service;

import com.sambound.erp.config.ImportConfiguration;
import com.sambound.erp.dto.SupplierImportResponse;
import com.sambound.erp.repository.SupplierRepository;
import com.sambound.erp.service.importing.ExcelImportService;
import com.sambound.erp.service.importing.exception.ImportException;
import com.sambound.erp.service.importing.exception.ImportProcessingException;
import com.sambound.erp.service.importing.monitor.Monitored;
import com.sambound.erp.service.importing.supplier.SupplierImportProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class SupplierImportService implements ExcelImportService<SupplierImportResponse> {

    private static final Logger logger = LoggerFactory.getLogger(SupplierImportService.class);

    private final SupplierRepository supplierRepository;
    private final TransactionTemplate transactionTemplate;
    private final ExecutorService executorService;
    private final ImportConfiguration importConfig;

    public SupplierImportService(
            SupplierRepository supplierRepository,
            PlatformTransactionManager transactionManager,
            ImportConfiguration importConfig) {
        this.supplierRepository = supplierRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
        this.transactionTemplate.setTimeout(importConfig.getTimeout().getTransactionTimeoutSeconds());
        this.executorService = Executors.newVirtualThreadPerTaskExecutor();
        this.importConfig = importConfig;
    }

    public SupplierImportResponse importFromExcel(MultipartFile file) {
        try {
            return importFromBytes(file.getBytes(), file.getOriginalFilename());
        } catch (ImportException e) {
            logger.error("供应商Excel/CSV文件导入失败: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("供应商Excel/CSV文件导入失败", e);
            throw new ImportProcessingException("供应商Excel/CSV文件导入失败: " + e.getMessage(), e);
        }
    }

    @Override
    @Monitored("供应商导入")
    public SupplierImportResponse importFromBytes(byte[] fileBytes, String fileName) {
        logger.info("开始导入供应商Excel/CSV文件: {}", fileName);
        SupplierImportProcessor processor = new SupplierImportProcessor(
                supplierRepository,
                transactionTemplate,
                executorService,
                importConfig
        );
        SupplierImportResponse result = processor.process(fileBytes, fileName);
        logger.info("供应商导入完成：总计 {} 条，成功 {} 条，失败 {} 条",
                result.supplierResult().totalRows(),
                result.supplierResult().successCount(),
                result.supplierResult().failureCount());
        return result;
    }
}
