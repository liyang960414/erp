package com.sambound.erp.service;

import com.sambound.erp.dto.SaleOutstockImportResponse;
import com.sambound.erp.repository.SaleOrderItemRepository;
import com.sambound.erp.repository.SaleOrderRepository;
import com.sambound.erp.repository.SaleOutstockRepository;
import com.sambound.erp.service.importing.sale.SaleOutstockImportProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

@Service
public class SaleOutstockImportService {

    private static final Logger logger = LoggerFactory.getLogger(SaleOutstockImportService.class);
    private static final int MAX_CONCURRENT_IMPORTS = 10;

    private final SaleOutstockRepository saleOutstockRepository;
    private final SaleOrderItemRepository saleOrderItemRepository;
    private final SaleOrderRepository saleOrderRepository;
    private final TransactionTemplate transactionTemplate;
    private final TransactionTemplate readOnlyTransactionTemplate;
    private final ExecutorService executorService;
    private final Semaphore importConcurrencySemaphore = new Semaphore(MAX_CONCURRENT_IMPORTS, true);

    public SaleOutstockImportService(
            SaleOutstockRepository saleOutstockRepository,
            SaleOrderItemRepository saleOrderItemRepository,
            SaleOrderRepository saleOrderRepository,
            PlatformTransactionManager transactionManager) {
        this.saleOutstockRepository = saleOutstockRepository;
        this.saleOrderItemRepository = saleOrderItemRepository;
        this.saleOrderRepository = saleOrderRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.transactionTemplate.setTimeout(120);
        this.readOnlyTransactionTemplate = new TransactionTemplate(transactionManager);
        this.readOnlyTransactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        this.readOnlyTransactionTemplate.setReadOnly(true);
        this.readOnlyTransactionTemplate.setTimeout(60);
        this.executorService = Executors.newVirtualThreadPerTaskExecutor();
    }

    public SaleOutstockImportResponse importFromExcel(MultipartFile file) {
        logger.info("开始导入销售出库Excel文件: {}", file.getOriginalFilename());

        try {
            byte[] fileBytes = file.getBytes();
            SaleOutstockImportProcessor processor = new SaleOutstockImportProcessor(
                    saleOutstockRepository,
                    saleOrderItemRepository,
                    saleOrderRepository,
                    transactionTemplate,
                    readOnlyTransactionTemplate,
                    executorService
            );
            SaleOutstockImportResponse response = processor.process(fileBytes);
            logger.info("销售出库导入完成：总计 {} 条，成功 {} 条，失败 {} 条",
                    response.result().totalRows(),
                    response.result().successCount(),
                    response.result().failureCount());
            return response;
        } catch (Exception e) {
            logger.error("销售出库Excel导入失败", e);
            throw new RuntimeException("销售出库Excel导入失败: " + e.getMessage(), e);
        }
    }
}

