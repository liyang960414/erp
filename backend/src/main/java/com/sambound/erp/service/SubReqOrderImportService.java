package com.sambound.erp.service;

import com.sambound.erp.dto.SubReqOrderImportResponse;
import com.sambound.erp.repository.BillOfMaterialRepository;
import com.sambound.erp.repository.MaterialRepository;
import com.sambound.erp.repository.SubReqOrderItemRepository;
import com.sambound.erp.repository.SubReqOrderRepository;
import com.sambound.erp.repository.SupplierRepository;
import com.sambound.erp.repository.UnitRepository;
import com.sambound.erp.service.importing.AbstractImportService;
import com.sambound.erp.service.importing.ImportServiceConfig;
import com.sambound.erp.service.importing.subreq.SubReqOrderImportProcessor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;

import java.io.InputStream;

@Service
public class SubReqOrderImportService extends AbstractImportService<SubReqOrderImportResponse> {

    private final SubReqOrderRepository subReqOrderRepository;
    private final SubReqOrderItemRepository subReqOrderItemRepository;
    private final SupplierRepository supplierRepository;
    private final MaterialRepository materialRepository;
    private final UnitRepository unitRepository;
    private final BillOfMaterialRepository bomRepository;

    public SubReqOrderImportService(
            SubReqOrderRepository subReqOrderRepository,
            SubReqOrderItemRepository subReqOrderItemRepository,
            SupplierRepository supplierRepository,
            MaterialRepository materialRepository,
            UnitRepository unitRepository,
            BillOfMaterialRepository bomRepository,
            PlatformTransactionManager transactionManager) {
        super(transactionManager, ImportServiceConfig.PURCHASE_ORDER_TRANSACTION_TIMEOUT);
        this.subReqOrderRepository = subReqOrderRepository;
        this.subReqOrderItemRepository = subReqOrderItemRepository;
        this.supplierRepository = supplierRepository;
        this.materialRepository = materialRepository;
        this.unitRepository = unitRepository;
        this.bomRepository = bomRepository;
    }

    @Override
    protected SubReqOrderImportResponse importFromInputStream(InputStream inputStream, String fileName, long fileSize) throws Exception {
        SubReqOrderImportProcessor processor = new SubReqOrderImportProcessor(
                subReqOrderRepository,
                subReqOrderItemRepository,
                supplierRepository,
                materialRepository,
                unitRepository,
                bomRepository,
                transactionTemplate,
                executorService
        );

        return processor.process(inputStream);
    }

    @Override
    protected void logImportResult(SubReqOrderImportResponse result) {
        logger.info("委外订单导入完成：总计 {} 条，成功 {} 条，失败 {} 条",
                result.subReqOrderResult().totalRows(),
                result.subReqOrderResult().successCount(),
                result.subReqOrderResult().failureCount());
    }

    /**
     * 从字节数组执行导入（兼容旧代码）
     */
    public SubReqOrderImportResponse importFromBytes(byte[] fileBytes, String fileName, long size) {
        return super.importFromBytes(fileBytes, fileName, size);
    }
}

