package com.sambound.erp.service;

import com.sambound.erp.dto.PurchaseOrderImportResponse;
import com.sambound.erp.repository.BillOfMaterialRepository;
import com.sambound.erp.repository.MaterialRepository;
import com.sambound.erp.repository.PurchaseOrderItemRepository;
import com.sambound.erp.repository.PurchaseOrderRepository;
import com.sambound.erp.repository.SubReqOrderItemRepository;
import com.sambound.erp.repository.SupplierRepository;
import com.sambound.erp.repository.UnitRepository;
import com.sambound.erp.service.importing.AbstractImportService;
import com.sambound.erp.service.importing.ImportServiceConfig;
import com.sambound.erp.service.importing.purchase.PurchaseOrderImportProcessor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;

import java.io.InputStream;

@Service
public class PurchaseOrderImportService extends AbstractImportService<PurchaseOrderImportResponse> {

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderItemRepository purchaseOrderItemRepository;
    private final SupplierRepository supplierRepository;
    private final MaterialRepository materialRepository;
    private final UnitRepository unitRepository;
    private final BillOfMaterialRepository bomRepository;
    private final SubReqOrderItemRepository subReqOrderItemRepository;

    public PurchaseOrderImportService(
            PurchaseOrderRepository purchaseOrderRepository,
            PurchaseOrderItemRepository purchaseOrderItemRepository,
            SupplierRepository supplierRepository,
            MaterialRepository materialRepository,
            UnitRepository unitRepository,
            BillOfMaterialRepository bomRepository,
            SubReqOrderItemRepository subReqOrderItemRepository,
            PlatformTransactionManager transactionManager) {
        super(transactionManager, ImportServiceConfig.PURCHASE_ORDER_TRANSACTION_TIMEOUT);
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.purchaseOrderItemRepository = purchaseOrderItemRepository;
        this.supplierRepository = supplierRepository;
        this.materialRepository = materialRepository;
        this.unitRepository = unitRepository;
        this.bomRepository = bomRepository;
        this.subReqOrderItemRepository = subReqOrderItemRepository;
    }

    @Override
    protected PurchaseOrderImportResponse importFromInputStream(InputStream inputStream, String fileName, long fileSize) throws Exception {
        PurchaseOrderImportProcessor processor = new PurchaseOrderImportProcessor(
                purchaseOrderRepository,
                purchaseOrderItemRepository,
                supplierRepository,
                materialRepository,
                unitRepository,
                bomRepository,
                subReqOrderItemRepository,
                transactionTemplate,
                executorService
        );

        return processor.process(inputStream);
    }

    @Override
    protected void logImportResult(PurchaseOrderImportResponse result) {
        logger.info("采购订单导入完成：总计 {} 条，成功 {} 条，失败 {} 条",
                result.purchaseOrderResult().totalRows(),
                result.purchaseOrderResult().successCount(),
                result.purchaseOrderResult().failureCount());
    }

    /**
     * 从字节数组执行导入（兼容旧代码）
     */
    public PurchaseOrderImportResponse importFromBytes(byte[] fileBytes, String fileName, long size) {
        return super.importFromBytes(fileBytes, fileName, size);
    }
}

