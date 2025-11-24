package com.sambound.erp.service;

import com.sambound.erp.dto.UnitImportResponse;
import com.sambound.erp.repository.UnitGroupRepository;
import com.sambound.erp.repository.UnitRepository;
import com.sambound.erp.service.importing.AbstractImportService;
import com.sambound.erp.service.importing.unit.UnitImportProcessor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;

import java.io.InputStream;

@Service
public class UnitImportService extends AbstractImportService<UnitImportResponse> {

    private final UnitService unitService;
    private final UnitRepository unitRepository;
    private final UnitGroupRepository unitGroupRepository;

    public UnitImportService(
            UnitService unitService,
            UnitRepository unitRepository,
            UnitGroupRepository unitGroupRepository,
            PlatformTransactionManager transactionManager) {
        super(transactionManager);
        this.unitService = unitService;
        this.unitRepository = unitRepository;
        this.unitGroupRepository = unitGroupRepository;
    }

    @Override
    protected UnitImportResponse importFromInputStream(InputStream inputStream, String fileName, long fileSize) throws Exception {
        UnitImportProcessor processor = new UnitImportProcessor(
                unitService,
                unitRepository,
                unitGroupRepository,
                transactionTemplate.getTransactionManager(),
                executorService
        );
        return processor.process(inputStream);
    }

    @Override
    protected void logImportResult(UnitImportResponse response) {
        logger.info("单位导入完成：总计 {} 条，成功 {} 条，失败 {} 条",
                response.totalRows(),
                response.successCount(),
                response.failureCount());
    }

    /**
     * 从字节数组执行导入（兼容旧代码）
     */
    public UnitImportResponse importFromBytes(byte[] fileBytes, String fileName) {
        return importFromBytes(fileBytes, fileName, fileBytes.length);
    }
}

