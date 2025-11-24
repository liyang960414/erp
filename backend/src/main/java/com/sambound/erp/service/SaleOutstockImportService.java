package com.sambound.erp.service;

import com.sambound.erp.dto.SaleOutstockImportResponse;
import com.sambound.erp.repository.SaleOrderItemRepository;
import com.sambound.erp.repository.SaleOrderRepository;
import com.sambound.erp.repository.SaleOutstockRepository;
import com.sambound.erp.service.importing.AbstractImportService;
import com.sambound.erp.service.importing.sale.SaleOutstockImportProcessor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.InputStream;

@Service
public class SaleOutstockImportService extends AbstractImportService<SaleOutstockImportResponse> {

    private final SaleOutstockRepository saleOutstockRepository;
    private final SaleOrderItemRepository saleOrderItemRepository;
    private final SaleOrderRepository saleOrderRepository;
    private final TransactionTemplate readOnlyTransactionTemplate;

    public SaleOutstockImportService(
            SaleOutstockRepository saleOutstockRepository,
            SaleOrderItemRepository saleOrderItemRepository,
            SaleOrderRepository saleOrderRepository,
            PlatformTransactionManager transactionManager) {
        super(transactionManager);
        this.saleOutstockRepository = saleOutstockRepository;
        this.saleOrderItemRepository = saleOrderItemRepository;
        this.saleOrderRepository = saleOrderRepository;
        
        // 创建只读事务模板
        this.readOnlyTransactionTemplate = new TransactionTemplate(transactionManager);
        this.readOnlyTransactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        this.readOnlyTransactionTemplate.setReadOnly(true);
        this.readOnlyTransactionTemplate.setTimeout(60);
    }

    @Override
    protected SaleOutstockImportResponse importFromInputStream(InputStream inputStream, String fileName, long fileSize) throws Exception {
        SaleOutstockImportProcessor processor = new SaleOutstockImportProcessor(
                saleOutstockRepository,
                saleOrderItemRepository,
                saleOrderRepository,
                transactionTemplate,
                readOnlyTransactionTemplate,
                executorService
        );
        return processor.process(inputStream);
    }

    @Override
    protected void logImportResult(SaleOutstockImportResponse response) {
        logger.info("销售出库导入完成：总计 {} 条，成功 {} 条，失败 {} 条",
                response.result().totalRows(),
                response.result().successCount(),
                response.result().failureCount());
    }

    /**
     * 从字节数组执行导入（兼容旧代码）
     */
    public SaleOutstockImportResponse importFromBytes(byte[] fileBytes, String fileName) {
        return importFromBytes(fileBytes, fileName, fileBytes.length);
    }
}

