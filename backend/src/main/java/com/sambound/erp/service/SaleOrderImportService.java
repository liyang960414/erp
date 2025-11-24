package com.sambound.erp.service;

import com.sambound.erp.dto.SaleOrderImportResponse;
import com.sambound.erp.repository.CustomerRepository;
import com.sambound.erp.repository.MaterialRepository;
import com.sambound.erp.repository.SaleOrderItemRepository;
import com.sambound.erp.repository.SaleOrderRepository;
import com.sambound.erp.repository.UnitRepository;
import com.sambound.erp.service.importing.AbstractImportService;
import com.sambound.erp.service.importing.sale.SaleOrderImportProcessor;
import com.sambound.erp.service.CustomerService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;

import java.io.InputStream;

@Service
public class SaleOrderImportService extends AbstractImportService<SaleOrderImportResponse> {

    private final SaleOrderRepository saleOrderRepository;
    private final SaleOrderItemRepository saleOrderItemRepository;
    private final CustomerRepository customerRepository;
    private final MaterialRepository materialRepository;
    private final UnitRepository unitRepository;
    private final CustomerService customerService;

    public SaleOrderImportService(
            SaleOrderRepository saleOrderRepository,
            SaleOrderItemRepository saleOrderItemRepository,
            CustomerRepository customerRepository,
            MaterialRepository materialRepository,
            UnitRepository unitRepository,
            CustomerService customerService,
            PlatformTransactionManager transactionManager) {
        super(transactionManager);
        this.saleOrderRepository = saleOrderRepository;
        this.saleOrderItemRepository = saleOrderItemRepository;
        this.customerRepository = customerRepository;
        this.materialRepository = materialRepository;
        this.unitRepository = unitRepository;
        this.customerService = customerService;
    }

    @Override
    protected SaleOrderImportResponse importFromInputStream(InputStream inputStream, String fileName, long fileSize) throws Exception {
        SaleOrderImportProcessor processor = new SaleOrderImportProcessor(
                saleOrderRepository,
                saleOrderItemRepository,
                customerRepository,
                materialRepository,
                unitRepository,
                customerService,
                transactionTemplate,
                executorService
        );

        return processor.process(inputStream);
    }

    @Override
    protected void logImportResult(SaleOrderImportResponse result) {
        logger.info("销售订单导入完成：总计 {} 条，成功 {} 条，失败 {} 条",
                result.saleOrderResult().totalRows(),
                result.saleOrderResult().successCount(),
                result.saleOrderResult().failureCount());
    }

    /**
     * 从字节数组执行导入（兼容旧代码）
     */
    public SaleOrderImportResponse importFromBytes(byte[] fileBytes, String fileName) {
        return importFromBytes(fileBytes, fileName, fileBytes.length);
    }
}
