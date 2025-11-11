package com.sambound.erp.service;

import com.sambound.erp.dto.UnitImportResponse;
import com.sambound.erp.repository.UnitGroupRepository;
import com.sambound.erp.repository.UnitRepository;
import com.sambound.erp.service.importing.unit.UnitImportProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.multipart.MultipartFile;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class UnitImportService {

    private static final Logger logger = LoggerFactory.getLogger(UnitImportService.class);

    private final UnitService unitService;
    private final UnitRepository unitRepository;
    private final UnitGroupRepository unitGroupRepository;
    private final PlatformTransactionManager transactionManager;
    private final ExecutorService executorService;

    public UnitImportService(
            UnitService unitService,
            UnitRepository unitRepository,
            UnitGroupRepository unitGroupRepository,
            PlatformTransactionManager transactionManager) {
        this.unitService = unitService;
        this.unitRepository = unitRepository;
        this.unitGroupRepository = unitGroupRepository;
        this.transactionManager = transactionManager;
        this.executorService = Executors.newVirtualThreadPerTaskExecutor();
    }

    public UnitImportResponse importFromExcel(MultipartFile file) {
        try {
            return importFromBytes(file.getBytes(), file.getOriginalFilename());
        } catch (Exception e) {
            logger.error("单位 Excel 导入失败", e);
            throw new RuntimeException("单位 Excel 导入失败: " + e.getMessage(), e);
        }
    }

    public UnitImportResponse importFromBytes(byte[] fileBytes, String fileName) {
        logger.info("开始导入单位 Excel 文件: {}", fileName);
        UnitImportProcessor processor = new UnitImportProcessor(
                unitService,
                unitRepository,
                unitGroupRepository,
                transactionManager,
                executorService
        );
        UnitImportResponse response = processor.process(fileBytes);
        logger.info("单位导入完成：总计 {} 条，成功 {} 条，失败 {} 条",
                response.totalRows(),
                response.successCount(),
                response.failureCount());
        return response;
    }
}

