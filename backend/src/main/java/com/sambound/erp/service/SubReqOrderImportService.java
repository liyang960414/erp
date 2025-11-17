package com.sambound.erp.service;

import com.sambound.erp.dto.SubReqOrderImportResponse;
import com.sambound.erp.repository.BillOfMaterialRepository;
import com.sambound.erp.repository.MaterialRepository;
import com.sambound.erp.repository.SubReqOrderItemRepository;
import com.sambound.erp.repository.SubReqOrderRepository;
import com.sambound.erp.repository.SupplierRepository;
import com.sambound.erp.repository.UnitRepository;
import com.sambound.erp.service.importing.subreq.SubReqOrderImportProcessor;
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
public class SubReqOrderImportService {

    private static final Logger logger = LoggerFactory.getLogger(SubReqOrderImportService.class);

    private final SubReqOrderRepository subReqOrderRepository;
    private final SubReqOrderItemRepository subReqOrderItemRepository;
    private final SupplierRepository supplierRepository;
    private final MaterialRepository materialRepository;
    private final UnitRepository unitRepository;
    private final BillOfMaterialRepository bomRepository;
    private final TransactionTemplate transactionTemplate;
    private final ExecutorService executorService;

    public SubReqOrderImportService(
            SubReqOrderRepository subReqOrderRepository,
            SubReqOrderItemRepository subReqOrderItemRepository,
            SupplierRepository supplierRepository,
            MaterialRepository materialRepository,
            UnitRepository unitRepository,
            BillOfMaterialRepository bomRepository,
            PlatformTransactionManager transactionManager) {
        this.subReqOrderRepository = subReqOrderRepository;
        this.subReqOrderItemRepository = subReqOrderItemRepository;
        this.supplierRepository = supplierRepository;
        this.materialRepository = materialRepository;
        this.unitRepository = unitRepository;
        this.bomRepository = bomRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
        this.transactionTemplate.setTimeout(1800);
        this.executorService = Executors.newVirtualThreadPerTaskExecutor();
    }

    public SubReqOrderImportResponse importFromExcel(MultipartFile file) {
        try {
            return importFromBytes(file.getBytes(), file.getOriginalFilename(), file.getSize());
        } catch (Exception e) {
            logger.error("Excel文件导入失败", e);
            throw new RuntimeException("Excel文件导入失败: " + e.getMessage(), e);
        }
    }

    public SubReqOrderImportResponse importFromBytes(byte[] fileBytes, String fileName, long size) {
        logger.info("开始导入委外订单Excel文件: {}，文件大小: {} MB",
                fileName,
                size / (1024.0 * 1024.0));

        try (InputStream inputStream = new java.io.ByteArrayInputStream(fileBytes)) {
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

            SubReqOrderImportResponse result = processor.process(inputStream);
            logger.info("委外订单导入完成：总计 {} 条，成功 {} 条，失败 {} 条",
                    result.subReqOrderResult().totalRows(),
                    result.subReqOrderResult().successCount(),
                    result.subReqOrderResult().failureCount());
            return result;
        } catch (Exception e) {
            logger.error("Excel文件导入失败", e);
            throw new RuntimeException("Excel文件导入失败: " + e.getMessage(), e);
        }
    }
}

