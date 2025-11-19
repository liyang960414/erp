package com.sambound.erp.service;

import com.sambound.erp.config.ImportConfiguration;
import com.sambound.erp.dto.SubReqOrderImportResponse;
import com.sambound.erp.repository.BillOfMaterialRepository;
import com.sambound.erp.repository.MaterialRepository;
import com.sambound.erp.repository.SubReqOrderItemRepository;
import com.sambound.erp.repository.SubReqOrderRepository;
import com.sambound.erp.repository.SupplierRepository;
import com.sambound.erp.repository.UnitRepository;
import com.sambound.erp.service.importing.ExcelImportService;
import com.sambound.erp.service.importing.exception.ImportException;
import com.sambound.erp.service.importing.exception.ImportProcessingException;
import com.sambound.erp.service.importing.monitor.Monitored;
import com.sambound.erp.service.importing.subreq.SubReqOrderImportProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class SubReqOrderImportService implements ExcelImportService<SubReqOrderImportResponse> {

    private static final Logger logger = LoggerFactory.getLogger(SubReqOrderImportService.class);

    private final SubReqOrderRepository subReqOrderRepository;
    private final SubReqOrderItemRepository subReqOrderItemRepository;
    private final SupplierRepository supplierRepository;
    private final MaterialRepository materialRepository;
    private final UnitRepository unitRepository;
    private final BillOfMaterialRepository bomRepository;
    private final TransactionTemplate transactionTemplate;
    private final ExecutorService executorService;
    private final ImportConfiguration importConfig;

    public SubReqOrderImportService(
            SubReqOrderRepository subReqOrderRepository,
            SubReqOrderItemRepository subReqOrderItemRepository,
            SupplierRepository supplierRepository,
            MaterialRepository materialRepository,
            UnitRepository unitRepository,
            BillOfMaterialRepository bomRepository,
            PlatformTransactionManager transactionManager,
            ImportConfiguration importConfig) {
        this.subReqOrderRepository = subReqOrderRepository;
        this.subReqOrderItemRepository = subReqOrderItemRepository;
        this.supplierRepository = supplierRepository;
        this.materialRepository = materialRepository;
        this.unitRepository = unitRepository;
        this.bomRepository = bomRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
        this.transactionTemplate.setTimeout(importConfig.getTimeout().getTransactionTimeoutSeconds());
        this.executorService = Executors.newVirtualThreadPerTaskExecutor();
        this.importConfig = importConfig;
    }

    public SubReqOrderImportResponse importFromExcel(MultipartFile file) {
        try {
            return importFromBytes(file.getBytes(), file.getOriginalFilename());
        } catch (ImportException e) {
            logger.error("委外订单Excel文件导入失败: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("委外订单Excel文件导入失败", e);
            throw new ImportProcessingException("委外订单Excel文件导入失败: " + e.getMessage(), e);
        }
    }

    @Override
    @Monitored("委外订单导入")
    public SubReqOrderImportResponse importFromBytes(byte[] fileBytes, String fileName) {
        logger.info("开始导入委外订单Excel文件: {}，文件大小: {} MB",
                fileName,
                fileBytes.length / (1024.0 * 1024.0));

        SubReqOrderImportProcessor processor = new SubReqOrderImportProcessor(
                subReqOrderRepository,
                subReqOrderItemRepository,
                supplierRepository,
                materialRepository,
                unitRepository,
                bomRepository,
                transactionTemplate,
                executorService,
                importConfig
        );

        SubReqOrderImportResponse result = processor.process(fileBytes, fileName);
        logger.info("委外订单导入完成：总计 {} 条，成功 {} 条，失败 {} 条",
                result.subReqOrderResult().totalRows(),
                result.subReqOrderResult().successCount(),
                result.subReqOrderResult().failureCount());
        return result;
    }
}
