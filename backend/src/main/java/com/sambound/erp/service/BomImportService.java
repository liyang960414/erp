package com.sambound.erp.service;

import com.sambound.erp.dto.BomImportResponse;
import com.sambound.erp.repository.BillOfMaterialRepository;
import com.sambound.erp.repository.BomItemRepository;
import com.sambound.erp.repository.MaterialRepository;
import com.sambound.erp.repository.UnitRepository;
import com.sambound.erp.service.importing.bom.BomImportProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
public class BomImportService {

    private static final Logger logger = LoggerFactory.getLogger(BomImportService.class);

    private final BillOfMaterialRepository bomRepository;
    private final BomItemRepository bomItemRepository;
    private final MaterialRepository materialRepository;
    private final UnitRepository unitRepository;
    private final TransactionTemplate transactionTemplate;

    public BomImportService(
            BillOfMaterialRepository bomRepository,
            BomItemRepository bomItemRepository,
            MaterialRepository materialRepository,
            UnitRepository unitRepository,
            PlatformTransactionManager transactionManager) {
        this.bomRepository = bomRepository;
        this.bomItemRepository = bomItemRepository;
        this.materialRepository = materialRepository;
        this.unitRepository = unitRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
        this.transactionTemplate.setTimeout(120);
    }

    public BomImportResponse importFromExcel(MultipartFile file) {
        try {
            return importFromBytes(file.getBytes(), file.getOriginalFilename());
        } catch (Exception e) {
            logger.error("BOM Excel 导入失败", e);
            throw new RuntimeException("BOM Excel 导入失败: " + e.getMessage(), e);
        }
    }

    public BomImportResponse importFromBytes(byte[] fileBytes, String fileName) {
        logger.info("开始导入 BOM Excel 文件: {}", fileName);
        BomImportProcessor processor = new BomImportProcessor(
                bomRepository,
                bomItemRepository,
                materialRepository,
                unitRepository,
                transactionTemplate
        );
        BomImportResponse response = processor.process(fileBytes);
        logger.info("BOM 导入完成：BOM {} 条，成功 {} 条，失败 {} 条；明细 {} 条，成功 {} 条，失败 {} 条",
                response.bomResult().totalRows(),
                response.bomResult().successCount(),
                response.bomResult().failureCount(),
                response.itemResult().totalRows(),
                response.itemResult().successCount(),
                response.itemResult().failureCount());
        return response;
    }
}

