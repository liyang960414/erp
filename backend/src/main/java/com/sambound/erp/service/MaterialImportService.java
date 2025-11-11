package com.sambound.erp.service;

import com.sambound.erp.dto.MaterialImportResponse;
import com.sambound.erp.repository.MaterialGroupRepository;
import com.sambound.erp.repository.MaterialRepository;
import com.sambound.erp.repository.UnitRepository;
import com.sambound.erp.service.importing.material.MaterialImportProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class MaterialImportService {

    private static final Logger logger = LoggerFactory.getLogger(MaterialImportService.class);

    private final MaterialGroupRepository materialGroupRepository;
    private final MaterialRepository materialRepository;
    private final UnitRepository unitRepository;
    private final TransactionTemplate transactionTemplate;
    private final ExecutorService executorService;

    public MaterialImportService(
            MaterialGroupRepository materialGroupRepository,
            MaterialRepository materialRepository,
            UnitRepository unitRepository,
            PlatformTransactionManager transactionManager) {
        this.materialGroupRepository = materialGroupRepository;
        this.materialRepository = materialRepository;
        this.unitRepository = unitRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
        this.transactionTemplate.setTimeout(120);
        this.executorService = Executors.newVirtualThreadPerTaskExecutor();
    }

    public MaterialImportResponse importFromExcel(MultipartFile file) {
        try {
            return importFromBytes(file.getBytes(), file.getOriginalFilename());
        } catch (Exception e) {
            logger.error("物料Excel文件导入失败", e);
            throw new RuntimeException("物料Excel文件导入失败: " + e.getMessage(), e);
        }
    }

    public MaterialImportResponse importFromBytes(byte[] fileBytes, String fileName) {
        logger.info("开始导入物料Excel文件: {}", fileName);
        MaterialImportProcessor processor = new MaterialImportProcessor(
                materialGroupRepository,
                materialRepository,
                unitRepository,
                transactionTemplate,
                executorService
        );
        MaterialImportResponse response = processor.process(fileBytes);
        logger.info("物料组总计 {} 条，物料总计 {} 条",
                response.unitGroupResult().totalRows(),
                response.materialResult().totalRows());
        return response;
    }
}

