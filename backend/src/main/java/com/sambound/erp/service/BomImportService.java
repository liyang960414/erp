package com.sambound.erp.service;

import com.sambound.erp.dto.BomImportResponse;
import com.sambound.erp.repository.BillOfMaterialRepository;
import com.sambound.erp.repository.BomItemRepository;
import com.sambound.erp.repository.MaterialRepository;
import com.sambound.erp.repository.UnitRepository;
import com.sambound.erp.service.importing.AbstractImportService;
import com.sambound.erp.service.importing.bom.BomImportProcessor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.InputStream;

@Service
public class BomImportService extends AbstractImportService<BomImportResponse> {

    private final BillOfMaterialRepository bomRepository;
    private final BomItemRepository bomItemRepository;
    private final MaterialRepository materialRepository;
    private final UnitRepository unitRepository;

    public BomImportService(
            BillOfMaterialRepository bomRepository,
            BomItemRepository bomItemRepository,
            MaterialRepository materialRepository,
            UnitRepository unitRepository,
            PlatformTransactionManager transactionManager) {
        super(transactionManager);
        this.bomRepository = bomRepository;
        this.bomItemRepository = bomItemRepository;
        this.materialRepository = materialRepository;
        this.unitRepository = unitRepository;
    }

    @Override
    protected BomImportResponse importFromInputStream(InputStream inputStream, String fileName, long fileSize) throws Exception {
        BomImportProcessor processor = new BomImportProcessor(
                bomRepository,
                bomItemRepository,
                materialRepository,
                unitRepository,
                transactionTemplate
        );
        return processor.process(inputStream);
    }

    @Override
    protected void logImportResult(BomImportResponse response) {
        logger.info("BOM 导入完成：BOM {} 条，成功 {} 条，失败 {} 条；明细 {} 条，成功 {} 条，失败 {} 条",
                response.bomResult().totalRows(),
                response.bomResult().successCount(),
                response.bomResult().failureCount(),
                response.itemResult().totalRows(),
                response.itemResult().successCount(),
                response.itemResult().failureCount());
    }

    /**
     * 从字节数组执行导入（兼容旧代码）
     */
    public BomImportResponse importFromBytes(byte[] fileBytes, String fileName) {
        return importFromBytes(fileBytes, fileName, fileBytes.length);
    }
}

