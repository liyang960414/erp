package com.sambound.erp.service.importing.supplier;

import cn.idev.excel.FastExcel;
import cn.idev.excel.context.AnalysisContext;
import cn.idev.excel.read.listener.ReadListener;
import com.sambound.erp.dto.SupplierImportResponse;
import com.sambound.erp.entity.Supplier;
import com.sambound.erp.repository.SupplierRepository;
import com.sambound.erp.service.importing.ImportError;
import com.sambound.erp.service.importing.dto.SupplierExcelRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 供应商导入处理器。
 */
public class SupplierImportProcessor implements ReadListener<SupplierExcelRow> {

    private static final Logger logger = LoggerFactory.getLogger(SupplierImportProcessor.class);
    private static final int MAX_ERROR_COUNT = 1000;
    private static final int BATCH_SIZE = 100;
    private static final int MAX_CONCURRENT_BATCHES = 10;

    private final SupplierRepository supplierRepository;
    private final TransactionTemplate transactionTemplate;
    private final ExecutorService executorService;

    private final List<SupplierData> supplierDataList = new ArrayList<>();
    private final AtomicInteger totalRows = new AtomicInteger(0);

    public SupplierImportProcessor(SupplierRepository supplierRepository,
                                   TransactionTemplate transactionTemplate,
                                   ExecutorService executorService) {
        this.supplierRepository = supplierRepository;
        this.transactionTemplate = transactionTemplate;
        this.executorService = executorService;
    }

    public SupplierImportResponse process(byte[] fileBytes) {
        supplierDataList.clear();
        totalRows.set(0);

        FastExcel.read(new ByteArrayInputStream(fileBytes), SupplierExcelRow.class, this)
                .sheet("供应商#单据头(FBillHead)")
                .headRowNumber(2)
                .doRead();

        return importToDatabase();
    }

    @Override
    public void invoke(SupplierExcelRow data, AnalysisContext context) {
        totalRows.incrementAndGet();
        int rowNum = context.readRowHolder().getRowIndex();

        if (isNotBlank(data.getCode()) && isNotBlank(data.getName())) {
            supplierDataList.add(new SupplierData(
                    rowNum,
                    data.getCode().trim(),
                    data.getName().trim(),
                    trim(data.getShortName()),
                    trim(data.getEnglishName()),
                    trim(data.getDescription())
            ));
        }
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext context) {
        logger.info("供应商数据收集完成，共 {} 条有效数据", supplierDataList.size());
    }

    private SupplierImportResponse importToDatabase() {
        if (supplierDataList.isEmpty()) {
            logger.info("未找到供应商数据");
            return new SupplierImportResponse(
                    new SupplierImportResponse.SupplierImportResult(0, 0, 0, new ArrayList<>())
            );
        }

        long startTime = System.currentTimeMillis();
        List<ImportError> errors = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger successCount = new AtomicInteger(0);
        Map<String, Supplier> existingSupplierMap = preloadExistingSuppliers();

        List<List<SupplierData>> batches = partition(supplierDataList, BATCH_SIZE);
        List<CompletableFuture<BatchResult>> futures = new ArrayList<>();
        Semaphore semaphore = new Semaphore(MAX_CONCURRENT_BATCHES);

        for (int i = 0; i < batches.size(); i++) {
            List<SupplierData> batch = batches.get(i);
            int batchIndex = i + 1;

            CompletableFuture<BatchResult> future = CompletableFuture.supplyAsync(() -> {
                try {
                    semaphore.acquire();
                    try {
                        logger.info("处理供应商批次 {}/{}，记录数: {}", batchIndex, batches.size(), batch.size());
                        int batchSuccess = transactionTemplate.execute(status ->
                                importBatch(batch, existingSupplierMap, errors));
                        return new BatchResult(batchSuccess, List.of());
                    } finally {
                        semaphore.release();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.warn("批次 {} 处理被中断", batchIndex);
                    return new BatchResult(0, buildBatchErrors(batch, "批次处理被中断"));
                } catch (Exception e) {
                    logger.error("批次 {} 导入失败", batchIndex, e);
                    return new BatchResult(0, buildBatchErrors(batch, "批次导入失败: " + e.getMessage()));
                }
            }, executorService);
            futures.add(future);
        }

        waitForBatches(futures, successCount, errors);

        long duration = System.currentTimeMillis() - startTime;
        logger.info("供应商导入完成：耗时 {}ms，总计 {} 条，成功 {} 条，失败 {} 条",
                duration, supplierDataList.size(), successCount.get(), supplierDataList.size() - successCount.get());

        List<SupplierImportResponse.ImportError> responseErrors = errors.stream()
                .map(err -> new SupplierImportResponse.ImportError(
                        "供应商#单据头(FBillHead)",
                        err.getRowNumber(),
                        err.getField(),
                        err.getMessage()))
                .toList();

        return new SupplierImportResponse(
                new SupplierImportResponse.SupplierImportResult(
                        supplierDataList.size(),
                        successCount.get(),
                        supplierDataList.size() - successCount.get(),
                        responseErrors)
        );
    }

    private int importBatch(List<SupplierData> batch,
                            Map<String, Supplier> existingSupplierMap,
                            List<ImportError> errors) {
        int successCount = 0;

        for (SupplierData data : batch) {
            try {
                Supplier existing = existingSupplierMap.get(data.code());
                if (existing != null) {
                    existing.setName(data.name());
                    existing.setShortName(data.shortName());
                    existing.setEnglishName(data.englishName());
                    existing.setDescription(data.description());
                    supplierRepository.save(existing);
                } else {
                    Supplier supplier = Supplier.builder()
                            .code(data.code())
                            .name(data.name())
                            .shortName(data.shortName())
                            .englishName(data.englishName())
                            .description(data.description())
                            .build();
                    supplierRepository.save(supplier);
                    existingSupplierMap.put(data.code(), supplier);
                }
                successCount++;
            } catch (Exception e) {
                logger.error("导入供应商失败，编码: {}", data.code(), e);
                if (errors.size() < MAX_ERROR_COUNT) {
                    errors.add(new ImportError(
                            "供应商", data.rowNumber(), "供应商编码",
                            "导入失败: " + e.getMessage()));
                }
            }
        }

        return successCount;
    }

    private Map<String, Supplier> preloadExistingSuppliers() {
        List<Supplier> suppliers = supplierRepository.findAll();
        return suppliers.stream().collect(Collectors.toMap(Supplier::getCode, supplier -> supplier));
    }

    private void waitForBatches(List<CompletableFuture<BatchResult>> futures,
                                AtomicInteger successCount,
                                List<ImportError> errors) {
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(10, TimeUnit.MINUTES);

            for (CompletableFuture<BatchResult> future : futures) {
                try {
                    BatchResult result = future.get();
                    successCount.addAndGet(result.successCount());
                    if (!result.errors().isEmpty()) {
                        synchronized (errors) {
                            errors.addAll(result.errors());
                        }
                    }
                } catch (Exception e) {
                    logger.error("获取批次结果失败", e);
                }
            }
        } catch (TimeoutException e) {
            logger.error("导入超时", e);
            futures.forEach(f -> f.cancel(true));
            throw new RuntimeException("导入超时，请检查数据量或重试", e);
        } catch (Exception e) {
            logger.error("批次处理失败", e);
            futures.forEach(f -> f.cancel(true));
            throw new RuntimeException("批次处理失败: " + e.getMessage(), e);
        }
    }

    private List<ImportError> buildBatchErrors(List<SupplierData> batch, String message) {
        List<ImportError> batchErrors = new ArrayList<>();
        for (SupplierData data : batch) {
            if (batchErrors.size() < MAX_ERROR_COUNT) {
                batchErrors.add(new ImportError("供应商", data.rowNumber(), "供应商编码", message));
            }
        }
        return batchErrors;
    }

    private List<List<SupplierData>> partition(List<SupplierData> data, int size) {
        List<List<SupplierData>> result = new ArrayList<>();
        for (int i = 0; i < data.size(); i += size) {
            result.add(new ArrayList<>(data.subList(i, Math.min(i + size, data.size()))));
        }
        return result;
    }

    private boolean isNotBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String trim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record SupplierData(
            int rowNumber,
            String code,
            String name,
            String shortName,
            String englishName,
            String description
    ) {
    }

    private record BatchResult(
            int successCount,
            List<ImportError> errors
    ) {
    }
}

