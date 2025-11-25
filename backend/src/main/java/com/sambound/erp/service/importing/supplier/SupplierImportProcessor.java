package com.sambound.erp.service.importing.supplier;

import cn.idev.excel.FastExcel;
import cn.idev.excel.context.AnalysisContext;
import cn.idev.excel.read.listener.ReadListener;
import com.sambound.erp.dto.SupplierImportResponse;
import com.sambound.erp.entity.Supplier;
import com.sambound.erp.repository.SupplierRepository;
import com.sambound.erp.service.importing.ImportError;
import com.sambound.erp.service.importing.ImportModuleConfig;
import com.sambound.erp.service.importing.dto.SupplierExcelRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 供应商导入处理器。
 */
public class SupplierImportProcessor implements ReadListener<SupplierExcelRow> {

    private static final Logger logger = LoggerFactory.getLogger(SupplierImportProcessor.class);

    private final SupplierRepository supplierRepository;
    private final TransactionTemplate transactionTemplate;
    private final ExecutorService executorService;
    private final ImportModuleConfig moduleConfig;
    private final int batchSize;
    private final int maxConcurrentBatches;
    private final int maxErrorCount;
    private final String moduleName;

    private final List<SupplierData> supplierDataList = new ArrayList<>();
    private final AtomicInteger totalRows = new AtomicInteger(0);

    public SupplierImportProcessor(SupplierRepository supplierRepository,
                                   TransactionTemplate transactionTemplate,
                                   ExecutorService executorService,
                                   ImportModuleConfig moduleConfig) {
        this.supplierRepository = supplierRepository;
        this.transactionTemplate = transactionTemplate;
        this.executorService = executorService;
        this.moduleConfig = moduleConfig == null
                ? ImportModuleConfig.defaultConfig("supplier")
                : moduleConfig;
        this.batchSize = this.moduleConfig.batchInsertSize();
        this.maxConcurrentBatches = this.moduleConfig.maxConcurrentBatches();
        this.maxErrorCount = this.moduleConfig.maxErrorCount();
        this.moduleName = this.moduleConfig.module();
    }

    /**
     * 从输入流处理导入（新方法，支持流式读取）
     */
    public SupplierImportResponse process(InputStream inputStream) {
        supplierDataList.clear();
        totalRows.set(0);

        FastExcel.read(inputStream, SupplierExcelRow.class, this)
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
        logger.debug("[ExcelImport] module={} event=collect_complete rows={}",
                moduleName, supplierDataList.size());
    }

    private SupplierImportResponse importToDatabase() {
        if (supplierDataList.isEmpty()) {
            logger.debug("[ExcelImport] module={} event=no_data", moduleName);
            return new SupplierImportResponse(
                    new SupplierImportResponse.SupplierImportResult(0, 0, 0, new ArrayList<>())
            );
        }

        long startTime = System.currentTimeMillis();
        List<ImportError> errors = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger successCount = new AtomicInteger(0);
        // 改为按需查询，不再一次性加载所有供应商
        Map<String, Supplier> existingSupplierMap = new ConcurrentHashMap<>();

        List<List<SupplierData>> batches = partition(supplierDataList, batchSize);
        List<CompletableFuture<BatchResult>> futures = new ArrayList<>();
        Semaphore semaphore = new Semaphore(maxConcurrentBatches);

        for (int i = 0; i < batches.size(); i++) {
            List<SupplierData> batch = batches.get(i);
            int batchIndex = i + 1;

            CompletableFuture<BatchResult> future = CompletableFuture.supplyAsync(() -> {
                try {
                    semaphore.acquire();
                    try {
                        logger.debug("[ExcelImport] module={} event=batch_start index={} total={} batchSize={}",
                                moduleName, batchIndex, batches.size(), batch.size());
                        int batchSuccess = transactionTemplate.execute(status ->
                                importBatch(batch, existingSupplierMap, errors));
                        return new BatchResult(batchSuccess, List.of());
                    } finally {
                        semaphore.release();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.warn("[ExcelImport] module={} event=batch_interrupted index={}",
                            moduleName, batchIndex);
                    return new BatchResult(0, buildBatchErrors(batch, "批次处理被中断"));
                } catch (Exception e) {
                    logger.error("[ExcelImport] module={} event=batch_failed index={}",
                            moduleName, batchIndex, e);
                    return new BatchResult(0, buildBatchErrors(batch, "批次导入失败: " + e.getMessage()));
                }
            }, executorService);
            futures.add(future);
        }

        waitForBatches(futures, successCount, errors);

        long duration = System.currentTimeMillis() - startTime;
        logger.info("[ExcelImport] module={} event=import_done durationMs={} total={} success={} failure={}",
                moduleName, duration, supplierDataList.size(),
                successCount.get(), supplierDataList.size() - successCount.get());

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

        // 批量查询当前批次需要的供应商（按需查询）
        Set<String> codesToQuery = batch.stream()
                .map(SupplierData::code)
                .filter(code -> !existingSupplierMap.containsKey(code))
                .collect(Collectors.toSet());

        if (!codesToQuery.isEmpty()) {
            // 批量查询供应商
            List<Supplier> suppliers = supplierRepository.findByCodeIn(new ArrayList<>(codesToQuery));
            for (Supplier supplier : suppliers) {
                existingSupplierMap.put(supplier.getCode(), supplier);
            }
        }

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
                logger.error("[ExcelImport] module={} event=row_failed code={}",
                        moduleName, data.code(), e);
                if (errors.size() < maxErrorCount) {
                    errors.add(new ImportError(
                            "供应商", data.rowNumber(), "供应商编码",
                            "导入失败: " + e.getMessage()));
                }
            }
        }

        return successCount;
    }

    private void waitForBatches(List<CompletableFuture<BatchResult>> futures,
                                AtomicInteger successCount,
                                List<ImportError> errors) {
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(moduleConfig.batchTimeoutMinutes(), TimeUnit.MINUTES);

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
                    logger.error("[ExcelImport] module={} event=batch_result_failed", moduleName, e);
                }
            }
        } catch (TimeoutException e) {
            logger.error("[ExcelImport] module={} event=timeout", moduleName, e);
            futures.forEach(f -> f.cancel(true));
            throw new RuntimeException("导入超时，请检查数据量或重试", e);
        } catch (Exception e) {
            logger.error("[ExcelImport] module={} event=failed", moduleName, e);
            futures.forEach(f -> f.cancel(true));
            throw new RuntimeException("批次处理失败: " + e.getMessage(), e);
        }
    }

    private List<ImportError> buildBatchErrors(List<SupplierData> batch, String message) {
        List<ImportError> batchErrors = new ArrayList<>();
        for (SupplierData data : batch) {
            if (batchErrors.size() < maxErrorCount) {
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

