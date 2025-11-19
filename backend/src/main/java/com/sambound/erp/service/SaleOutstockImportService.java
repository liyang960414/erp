package com.sambound.erp.service;

import com.sambound.erp.config.ImportConfiguration;
import com.sambound.erp.dto.SaleOutstockImportResponse;
import com.sambound.erp.repository.SaleOrderItemRepository;
import com.sambound.erp.repository.SaleOrderRepository;
import com.sambound.erp.repository.SaleOutstockRepository;
import com.sambound.erp.service.importing.ExcelImportService;
import com.sambound.erp.service.importing.exception.ImportException;
import com.sambound.erp.service.importing.exception.ImportProcessingException;
import com.sambound.erp.service.importing.monitor.Monitored;
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

@Service
public class SaleOutstockImportService implements ExcelImportService<SaleOutstockImportResponse> {

    private static final Logger logger = LoggerFactory.getLogger(SaleOutstockImportService.class);

    private final SaleOutstockRepository saleOutstockRepository;
    private final SaleOrderItemRepository saleOrderItemRepository;
    private final SaleOrderRepository saleOrderRepository;
    private final TransactionTemplate transactionTemplate;
    private final TransactionTemplate readOnlyTransactionTemplate;
    private final ExecutorService executorService;
    private final ImportConfiguration importConfig;

    public SaleOutstockImportService(
            SaleOutstockRepository saleOutstockRepository,
            SaleOrderItemRepository saleOrderItemRepository,
            SaleOrderRepository saleOrderRepository,
            PlatformTransactionManager transactionManager,
            ImportConfiguration importConfig) {
        this.saleOutstockRepository = saleOutstockRepository;
        this.saleOrderItemRepository = saleOrderItemRepository;
        this.saleOrderRepository = saleOrderRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.transactionTemplate.setTimeout(importConfig.getTimeout().getTransactionTimeoutSeconds());
        this.readOnlyTransactionTemplate = new TransactionTemplate(transactionManager);
        this.readOnlyTransactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        this.readOnlyTransactionTemplate.setReadOnly(true);
        this.readOnlyTransactionTemplate.setTimeout(importConfig.getTimeout().getTransactionTimeoutSeconds());
        this.executorService = Executors.newVirtualThreadPerTaskExecutor();
        this.importConfig = importConfig;
    }

    public SaleOutstockImportResponse importFromExcel(MultipartFile file) {
        try {
            return importFromBytes(file.getBytes(), file.getOriginalFilename());
        } catch (ImportException e) {
            logger.error("销售出库Excel文件导入失败: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("销售出库Excel文件导入失败", e);
            throw new ImportProcessingException("销售出库Excel文件导入失败: " + e.getMessage(), e);
        }
    }

    @Override
    @Monitored("销售出库导入")
    public SaleOutstockImportResponse importFromBytes(byte[] fileBytes, String fileName) {
        logger.info("开始导入销售出库Excel文件: {}，文件大小: {} MB",
                fileName,
                fileBytes.length / (1024.0 * 1024.0));

        SaleOutstockImportProcessor processor = new SaleOutstockImportProcessor(
                saleOutstockRepository,
                saleOrderItemRepository,
                saleOrderRepository,
                transactionTemplate,
                readOnlyTransactionTemplate,
                executorService,
                importConfig
        );
        SaleOutstockImportResponse response = processor.process(fileBytes, fileName);
        logger.info("销售出库导入完成：总计 {} 条，成功 {} 条，失败 {} 条",
                response.result().totalRows(),
                response.result().successCount(),
                response.result().failureCount());
        return response;
    }
}
