package com.sambound.erp.service;

import com.sambound.erp.dto.PurchaseOrderImportResponse;
import com.sambound.erp.repository.BillOfMaterialRepository;
import com.sambound.erp.repository.MaterialRepository;
import com.sambound.erp.repository.PurchaseOrderItemRepository;
import com.sambound.erp.repository.PurchaseOrderRepository;
import com.sambound.erp.repository.SupplierRepository;
import com.sambound.erp.repository.UnitRepository;
import com.sambound.erp.service.importing.purchase.PurchaseOrderImportProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class PurchaseOrderImportService {

    private static final Logger logger = LoggerFactory.getLogger(PurchaseOrderImportService.class);

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderItemRepository purchaseOrderItemRepository;
    private final SupplierRepository supplierRepository;
    private final MaterialRepository materialRepository;
    private final UnitRepository unitRepository;
    private final BillOfMaterialRepository bomRepository;
    private final TransactionTemplate transactionTemplate;
    private final ExecutorService executorService;

    public PurchaseOrderImportService(
            PurchaseOrderRepository purchaseOrderRepository,
            PurchaseOrderItemRepository purchaseOrderItemRepository,
            SupplierRepository supplierRepository,
            MaterialRepository materialRepository,
            UnitRepository unitRepository,
            BillOfMaterialRepository bomRepository,
            PlatformTransactionManager transactionManager) {
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.purchaseOrderItemRepository = purchaseOrderItemRepository;
        this.supplierRepository = supplierRepository;
        this.materialRepository = materialRepository;
        this.unitRepository = unitRepository;
        this.bomRepository = bomRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
        this.transactionTemplate.setTimeout(1800);
        this.executorService = Executors.newVirtualThreadPerTaskExecutor();
    }

    public PurchaseOrderImportResponse importFromExcel(MultipartFile file) {
        logger.info("开始导入采购订单Excel文件: {}，文件大小: {} MB",
                file.getOriginalFilename(),
                file.getSize() / (1024.0 * 1024.0));

        try (InputStream inputStream = file.getInputStream()) {
            PurchaseOrderImportProcessor processor = new PurchaseOrderImportProcessor(
                    purchaseOrderRepository,
                    purchaseOrderItemRepository,
                    supplierRepository,
                    materialRepository,
                    unitRepository,
                    bomRepository,
                    transactionTemplate,
                    executorService
            );

            PurchaseOrderImportResponse result = processor.process(inputStream);
            logger.info("采购订单导入完成：总计 {} 条，成功 {} 条，失败 {} 条",
                    result.purchaseOrderResult().totalRows(),
                    result.purchaseOrderResult().successCount(),
                    result.purchaseOrderResult().failureCount());
            return result;
        } catch (Exception e) {
            logger.error("Excel文件导入失败", e);
            throw new RuntimeException("Excel文件导入失败: " + e.getMessage(), e);
        }
    }
}

