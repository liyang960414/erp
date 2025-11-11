package com.sambound.erp.service;

import com.sambound.erp.dto.SupplierImportResponse;
import com.sambound.erp.repository.SupplierRepository;
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
public class SupplierImportService {

    private static final Logger logger = LoggerFactory.getLogger(SupplierImportService.class);

    private final SupplierRepository supplierRepository;
    private final TransactionTemplate transactionTemplate;
    private final ExecutorService executorService;

    public SupplierImportService(
            SupplierRepository supplierRepository,
            PlatformTransactionManager transactionManager) {
        this.supplierRepository = supplierRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
        this.transactionTemplate.setTimeout(120);
        this.executorService = Executors.newVirtualThreadPerTaskExecutor();
    }

    public SupplierImportResponse importFromExcel(MultipartFile file) {
        logger.info("开始导入供应商Excel/CSV文件: {}", file.getOriginalFilename());

        try {
            byte[] fileBytes = file.getBytes();
            SupplierImportProcessor processor = new SupplierImportProcessor(
                    supplierRepository,
                    transactionTemplate,
                    executorService
            );

            SupplierImportResponse result = processor.process(fileBytes);
            logger.info("供应商导入完成：总计 {} 条，成功 {} 条，失败 {} 条",
                    result.supplierResult().totalRows(),
                    result.supplierResult().successCount(),
                    result.supplierResult().failureCount());
            return result;
        } catch (Exception e) {
            logger.error("Excel/CSV文件导入失败", e);
            throw new RuntimeException("Excel/CSV文件导入失败: " + e.getMessage(), e);
        }
    }
}

