package com.sambound.erp.service;

import com.sambound.erp.config.ImportConfiguration;
import com.sambound.erp.dto.PurchaseOrderImportResponse;
import com.sambound.erp.repository.BillOfMaterialRepository;
import com.sambound.erp.repository.MaterialRepository;
import com.sambound.erp.repository.PurchaseOrderItemRepository;
import com.sambound.erp.repository.PurchaseOrderRepository;
import com.sambound.erp.repository.SubReqOrderItemRepository;
import com.sambound.erp.repository.SupplierRepository;
import com.sambound.erp.repository.UnitRepository;
import com.sambound.erp.service.importing.ExcelImportService;
import com.sambound.erp.service.importing.exception.ImportException;
import com.sambound.erp.service.importing.exception.ImportProcessingException;
import com.sambound.erp.service.importing.monitor.Monitored;
import com.sambound.erp.service.importing.purchase.PurchaseOrderImportProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class PurchaseOrderImportService implements ExcelImportService<PurchaseOrderImportResponse> {

    private static final Logger logger = LoggerFactory.getLogger(PurchaseOrderImportService.class);

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderItemRepository purchaseOrderItemRepository;
    private final SupplierRepository supplierRepository;
    private final MaterialRepository materialRepository;
    private final UnitRepository unitRepository;
    private final BillOfMaterialRepository bomRepository;
    private final SubReqOrderItemRepository subReqOrderItemRepository;
    private final TransactionTemplate transactionTemplate;
    private final ExecutorService executorService;
    private final ImportConfiguration importConfig;

    public PurchaseOrderImportService(
            PurchaseOrderRepository purchaseOrderRepository,
            PurchaseOrderItemRepository purchaseOrderItemRepository,
            SupplierRepository supplierRepository,
            MaterialRepository materialRepository,
            UnitRepository unitRepository,
            BillOfMaterialRepository bomRepository,
            SubReqOrderItemRepository subReqOrderItemRepository,
            PlatformTransactionManager transactionManager,
            ImportConfiguration importConfig) {
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.purchaseOrderItemRepository = purchaseOrderItemRepository;
        this.supplierRepository = supplierRepository;
        this.materialRepository = materialRepository;
        this.unitRepository = unitRepository;
        this.bomRepository = bomRepository;
        this.subReqOrderItemRepository = subReqOrderItemRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
        this.transactionTemplate.setTimeout(importConfig.getTimeout().getTransactionTimeoutSeconds());
        this.executorService = Executors.newVirtualThreadPerTaskExecutor();
        this.importConfig = importConfig;
    }

    public PurchaseOrderImportResponse importFromExcel(MultipartFile file) {
        try {
            return importFromBytes(file.getBytes(), file.getOriginalFilename());
        } catch (ImportException e) {
            logger.error("采购订单Excel文件导入失败: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("采购订单Excel文件导入失败", e);
            throw new ImportProcessingException("采购订单Excel文件导入失败: " + e.getMessage(), e);
        }
    }

    @Override
    @Monitored("采购订单导入")
    public PurchaseOrderImportResponse importFromBytes(byte[] fileBytes, String fileName) {
        logger.info("开始导入采购订单Excel文件: {}，文件大小: {} MB",
                fileName,
                fileBytes.length / (1024.0 * 1024.0));

        PurchaseOrderImportProcessor processor = new PurchaseOrderImportProcessor(
                purchaseOrderRepository,
                purchaseOrderItemRepository,
                supplierRepository,
                materialRepository,
                unitRepository,
                bomRepository,
                subReqOrderItemRepository,
                transactionTemplate,
                executorService,
                importConfig
        );

        PurchaseOrderImportResponse result = processor.process(fileBytes, fileName);
        logger.info("采购订单导入完成：总计 {} 条，成功 {} 条，失败 {} 条",
                result.purchaseOrderResult().totalRows(),
                result.purchaseOrderResult().successCount(),
                result.purchaseOrderResult().failureCount());
        return result;
    }
}
