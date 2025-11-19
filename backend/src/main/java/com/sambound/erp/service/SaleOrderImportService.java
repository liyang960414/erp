package com.sambound.erp.service;

import com.sambound.erp.config.ImportConfiguration;
import com.sambound.erp.dto.SaleOrderImportResponse;
import com.sambound.erp.repository.CustomerRepository;
import com.sambound.erp.repository.MaterialRepository;
import com.sambound.erp.repository.SaleOrderItemRepository;
import com.sambound.erp.repository.SaleOrderRepository;
import com.sambound.erp.repository.UnitRepository;
import com.sambound.erp.service.importing.ExcelImportService;
import com.sambound.erp.service.importing.exception.ImportException;
import com.sambound.erp.service.importing.exception.ImportProcessingException;
import com.sambound.erp.service.importing.monitor.Monitored;
import com.sambound.erp.service.importing.sale.SaleOrderImportProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class SaleOrderImportService implements ExcelImportService<SaleOrderImportResponse> {

    private static final Logger logger = LoggerFactory.getLogger(SaleOrderImportService.class);

    private final SaleOrderRepository saleOrderRepository;
    private final SaleOrderItemRepository saleOrderItemRepository;
    private final CustomerRepository customerRepository;
    private final MaterialRepository materialRepository;
    private final UnitRepository unitRepository;
    private final CustomerService customerService;
    private final TransactionTemplate transactionTemplate;
    private final ExecutorService executorService;
    private final ImportConfiguration importConfig;

    public SaleOrderImportService(
            SaleOrderRepository saleOrderRepository,
            SaleOrderItemRepository saleOrderItemRepository,
            CustomerRepository customerRepository,
            MaterialRepository materialRepository,
            UnitRepository unitRepository,
            CustomerService customerService,
            PlatformTransactionManager transactionManager,
            ImportConfiguration importConfig) {
        this.saleOrderRepository = saleOrderRepository;
        this.saleOrderItemRepository = saleOrderItemRepository;
        this.customerRepository = customerRepository;
        this.materialRepository = materialRepository;
        this.unitRepository = unitRepository;
        this.customerService = customerService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
        this.transactionTemplate.setTimeout(importConfig.getTimeout().getTransactionTimeoutSeconds());
        this.executorService = Executors.newVirtualThreadPerTaskExecutor();
        this.importConfig = importConfig;
    }

    public SaleOrderImportResponse importFromExcel(MultipartFile file) {
        try {
            return importFromBytes(file.getBytes(), file.getOriginalFilename());
        } catch (ImportException e) {
            logger.error("销售订单Excel文件导入失败: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("销售订单Excel文件导入失败", e);
            throw new ImportProcessingException("销售订单Excel文件导入失败: " + e.getMessage(), e);
        }
    }

    @Override
    @Monitored("销售订单导入")
    public SaleOrderImportResponse importFromBytes(byte[] fileBytes, String fileName) {
        logger.info("开始导入销售订单Excel文件: {}，文件大小: {} MB",
                fileName,
                fileBytes.length / (1024.0 * 1024.0));

        SaleOrderImportProcessor processor = new SaleOrderImportProcessor(
                saleOrderRepository,
                saleOrderItemRepository,
                customerRepository,
                materialRepository,
                unitRepository,
                customerService,
                transactionTemplate,
                executorService,
                importConfig
        );

        SaleOrderImportResponse result = processor.process(fileBytes, fileName);
        logger.info("销售订单导入完成：总计 {} 条，成功 {} 条，失败 {} 条",
                result.saleOrderResult().totalRows(),
                result.saleOrderResult().successCount(),
                result.saleOrderResult().failureCount());
        return result;
    }
}
