package com.sambound.erp.service;

import com.sambound.erp.dto.MaterialImportResponse;
import com.sambound.erp.repository.MaterialGroupRepository;
import com.sambound.erp.repository.MaterialRepository;
import com.sambound.erp.repository.UnitRepository;
import com.sambound.erp.service.importing.AbstractImportService;
import com.sambound.erp.service.importing.material.MaterialImportProcessor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;

import java.io.InputStream;

@Service
public class MaterialImportService extends AbstractImportService<MaterialImportResponse> {

    private final MaterialGroupRepository materialGroupRepository;
    private final MaterialRepository materialRepository;
    private final UnitRepository unitRepository;

    public MaterialImportService(
            MaterialGroupRepository materialGroupRepository,
            MaterialRepository materialRepository,
            UnitRepository unitRepository,
            PlatformTransactionManager transactionManager) {
        super(transactionManager);
        this.materialGroupRepository = materialGroupRepository;
        this.materialRepository = materialRepository;
        this.unitRepository = unitRepository;
    }

    @Override
    protected MaterialImportResponse importFromInputStream(InputStream inputStream, String fileName, long fileSize) throws Exception {
        MaterialImportProcessor processor = new MaterialImportProcessor(
                materialGroupRepository,
                materialRepository,
                unitRepository,
                transactionTemplate,
                executorService
        );
        return processor.process(inputStream);
    }

    @Override
    protected void logImportResult(MaterialImportResponse result) {
        logger.info("物料组总计 {} 条，物料总计 {} 条",
                result.unitGroupResult().totalRows(),
                result.materialResult().totalRows());
    }

    /**
     * 从字节数组执行导入（兼容旧代码）
     */
    public MaterialImportResponse importFromBytes(byte[] fileBytes, String fileName) {
        return importFromBytes(fileBytes, fileName, fileBytes.length);
    }
}

