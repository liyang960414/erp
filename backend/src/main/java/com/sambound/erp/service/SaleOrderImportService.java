package com.sambound.erp.service;

import com.sambound.erp.dto.SaleOrderImportResponse;
import com.sambound.erp.repository.CustomerRepository;
import com.sambound.erp.repository.MaterialRepository;
import com.sambound.erp.repository.SaleOrderItemRepository;
import com.sambound.erp.repository.SaleOrderRepository;
import com.sambound.erp.repository.UnitRepository;
import com.sambound.erp.service.importing.sale.SaleOrderImportProcessor;
import com.sambound.erp.service.CustomerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class SaleOrderImportService {

    private static final Logger logger = LoggerFactory.getLogger(SaleOrderImportService.class);

    private final SaleOrderRepository saleOrderRepository;
    private final SaleOrderItemRepository saleOrderItemRepository;
    private final CustomerRepository customerRepository;
    private final MaterialRepository materialRepository;
    private final UnitRepository unitRepository;
    private final CustomerService customerService;
    private final TransactionTemplate transactionTemplate;
    private final ExecutorService executorService;

    public SaleOrderImportService(
            SaleOrderRepository saleOrderRepository,
            SaleOrderItemRepository saleOrderItemRepository,
            CustomerRepository customerRepository,
            MaterialRepository materialRepository,
            UnitRepository unitRepository,
            CustomerService customerService,
            PlatformTransactionManager transactionManager) {
        this.saleOrderRepository = saleOrderRepository;
        this.saleOrderItemRepository = saleOrderItemRepository;
        this.customerRepository = customerRepository;
        this.materialRepository = materialRepository;
        this.unitRepository = unitRepository;
        this.customerService = customerService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
        this.transactionTemplate.setTimeout(120);
        this.executorService = Executors.newVirtualThreadPerTaskExecutor();
    }

    public SaleOrderImportResponse importFromExcel(MultipartFile file) {
        try {
            return importFromBytes(file.getBytes(), file.getOriginalFilename());
        } catch (Exception e) {
            logger.error("Excel文件导入失败", e);
            throw new RuntimeException("Excel文件导入失败: " + e.getMessage(), e);
        }
    }

    public SaleOrderImportResponse importFromBytes(byte[] fileBytes, String fileName) {
        logger.info("开始导入销售订单Excel文件: {}", fileName);

        SaleOrderImportProcessor processor = new SaleOrderImportProcessor(
                saleOrderRepository,
                saleOrderItemRepository,
                customerRepository,
                materialRepository,
                unitRepository,
                customerService,
                transactionTemplate,
                executorService
        );

        SaleOrderImportResponse result = processor.process(fileBytes);
        logger.info("销售订单导入完成：总计 {} 条，成功 {} 条，失败 {} 条",
                result.saleOrderResult().totalRows(),
                result.saleOrderResult().successCount(),
                result.saleOrderResult().failureCount());
        return result;
    }
}
