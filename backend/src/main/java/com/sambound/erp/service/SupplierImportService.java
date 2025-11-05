package com.sambound.erp.service;

import cn.idev.excel.FastExcel;
import cn.idev.excel.context.AnalysisContext;
import cn.idev.excel.read.listener.ReadListener;
import com.sambound.erp.dto.SupplierExcelRow;
import com.sambound.erp.dto.SupplierImportResponse;
import com.sambound.erp.entity.Supplier;
import com.sambound.erp.repository.SupplierRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class SupplierImportService {
    
    private static final Logger logger = LoggerFactory.getLogger(SupplierImportService.class);
    private static final int MAX_ERROR_COUNT = 1000;
    private static final int BATCH_SIZE = 100; // 每批处理的供应商数量
    private static final int MAX_CONCURRENT_BATCHES = 10; // 最大并发批次数量
    
    private final SupplierRepository supplierRepository;
    private final TransactionTemplate transactionTemplate;
    private final ExecutorService executorService;
    
    public SupplierImportService(
            SupplierRepository supplierRepository,
            PlatformTransactionManager transactionManager) {
        this.supplierRepository = supplierRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
        this.transactionTemplate.setTimeout(120);
        // 使用虚拟线程执行器（Java 21+ Virtual Threads）
        this.executorService = Executors.newVirtualThreadPerTaskExecutor();
    }
    
    public SupplierImportResponse importFromExcel(MultipartFile file) {
        logger.info("开始导入供应商Excel/CSV文件: {}", file.getOriginalFilename());
        
        try {
            byte[] fileBytes = file.getBytes();
            
            SupplierDataCollector collector = new SupplierDataCollector();
            FastExcel.read(new ByteArrayInputStream(fileBytes), SupplierExcelRow.class, collector)
                    .sheet("供应商#单据头(FBillHead)")
                    .headRowNumber(2)  // 前两行为表头
                    .doRead();
            
            SupplierImportResponse result = collector.importToDatabase();
            logger.info("供应商导入完成：总计 {} 条，成功 {} 条，失败 {} 条",
                    result.supplierResult().totalRows(), 
                    result.supplierResult().successCount(), 
                    result.supplierResult().failureCount());
            
            return result;
        } catch (Exception e) {
            logger.error("Excel/CSV文件导入失败", e);
            throw new RuntimeException("Excel/CSV文件导入失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 供应商数据收集器
     */
    private class SupplierDataCollector implements ReadListener<SupplierExcelRow> {
        private final List<SupplierData> supplierDataList = new ArrayList<>();
        private final AtomicInteger totalRows = new AtomicInteger(0);
        
        @Override
        public void invoke(SupplierExcelRow data, AnalysisContext context) {
            totalRows.incrementAndGet();
            int rowNum = context.readRowHolder().getRowIndex();
            
            // 只处理有效的供应商数据（编码和名称不为空）
            if (data.getCode() != null && !data.getCode().trim().isEmpty() &&
                data.getName() != null && !data.getName().trim().isEmpty()) {
                
                SupplierData supplierData = new SupplierData(
                        rowNum,
                        data.getCode().trim(),
                        data.getName().trim(),
                        data.getShortName() != null ? data.getShortName().trim() : null,
                        data.getEnglishName() != null ? data.getEnglishName().trim() : null,
                        data.getDescription() != null ? data.getDescription().trim() : null
                );
                supplierDataList.add(supplierData);
            }
        }
        
        @Override
        public void doAfterAllAnalysed(AnalysisContext context) {
            logger.info("供应商数据收集完成，共 {} 条有效数据", supplierDataList.size());
        }
        
        public SupplierImportResponse importToDatabase() {
            if (supplierDataList.isEmpty()) {
                logger.info("未找到供应商数据");
                return new SupplierImportResponse(
                        new SupplierImportResponse.SupplierImportResult(0, 0, 0, new ArrayList<>())
                );
            }
            
            long startTime = System.currentTimeMillis();
            
            // 使用线程安全的集合收集错误
            List<SupplierImportResponse.ImportError> errors = Collections.synchronizedList(new ArrayList<>());
            AtomicInteger successCount = new AtomicInteger(0);
            
            // 预先批量查询所有已存在的供应商
            Map<String, Supplier> existingSupplierMap = preloadExistingSuppliers();
            
            // 将供应商数据分批处理
            int totalBatches = (supplierDataList.size() + BATCH_SIZE - 1) / BATCH_SIZE;
            logger.info("开始并行处理 {} 个批次，最大并发数: {}", totalBatches, MAX_CONCURRENT_BATCHES);
            
            List<CompletableFuture<BatchResult>> futures = new ArrayList<>();
            Semaphore batchSemaphore = new Semaphore(MAX_CONCURRENT_BATCHES);
            
            // 提交所有批次任务到线程池
            for (int i = 0; i < supplierDataList.size(); i += BATCH_SIZE) {
                int end = Math.min(i + BATCH_SIZE, supplierDataList.size());
                List<SupplierData> batch = new ArrayList<>(supplierDataList.subList(i, end));
                int batchIndex = (i / BATCH_SIZE) + 1;
                
                // 异步提交批次处理任务
                CompletableFuture<BatchResult> future = CompletableFuture.supplyAsync(() -> {
                    try {
                        // 获取信号量许可，限制并发数量
                        batchSemaphore.acquire();
                        try {
                            long batchStartTime = System.currentTimeMillis();
                            logger.info("处理批次 {}/{}，供应商数量: {}", batchIndex, totalBatches, batch.size());
                            
                            // 每个批次使用独立事务
                            int batchSuccess = transactionTemplate.execute(status -> {
                                return importBatchSuppliers(batch, existingSupplierMap, errors);
                            });
                            
                            long batchDuration = System.currentTimeMillis() - batchStartTime;
                            logger.info("批次 {}/{} 完成，耗时: {}ms，成功: {} 条", 
                                    batchIndex, totalBatches, batchDuration, batchSuccess);
                            
                            return new BatchResult(batchSuccess, new ArrayList<>());
                        } finally {
                            // 释放信号量许可
                            batchSemaphore.release();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        logger.warn("批次 {} 处理被中断", batchIndex);
                        List<SupplierImportResponse.ImportError> batchErrors = new ArrayList<>();
                        for (SupplierData data : batch) {
                            if (batchErrors.size() < MAX_ERROR_COUNT) {
                                batchErrors.add(new SupplierImportResponse.ImportError(
                                        "供应商", data.rowNumber, "编码",
                                        "批次处理被中断"));
                            }
                        }
                        return new BatchResult(0, batchErrors);
                    } catch (Exception e) {
                        logger.error("批次 {} 导入失败", batchIndex, e);
                        List<SupplierImportResponse.ImportError> batchErrors = new ArrayList<>();
                        for (SupplierData data : batch) {
                            if (batchErrors.size() < MAX_ERROR_COUNT) {
                                batchErrors.add(new SupplierImportResponse.ImportError(
                                        "供应商", data.rowNumber, "编码",
                                        "批次导入失败: " + e.getMessage()));
                            }
                        }
                        return new BatchResult(0, batchErrors);
                    }
                }, executorService);
                
                futures.add(future);
            }
            
            // 等待所有批次完成
            try {
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                        .get(30, TimeUnit.MINUTES);
                
                // 收集所有批次的结果
                for (CompletableFuture<BatchResult> future : futures) {
                    try {
                        BatchResult result = future.get();
                        successCount.addAndGet(result.successCount);
                        if (!result.errors.isEmpty()) {
                            synchronized (errors) {
                                errors.addAll(result.errors);
                            }
                        }
                    } catch (Exception e) {
                        logger.error("获取批次结果失败", e);
                    }
                }
            } catch (TimeoutException e) {
                logger.error("导入超时", e);
                // 取消未完成的任务
                futures.forEach(f -> f.cancel(true));
                throw new RuntimeException("导入超时，请检查数据量或重试", e);
            } catch (Exception e) {
                logger.error("批次处理失败", e);
                // 取消未完成的任务
                futures.forEach(f -> f.cancel(true));
                throw new RuntimeException("批次处理失败: " + e.getMessage(), e);
            }
            
            long totalDuration = System.currentTimeMillis() - startTime;
            int totalSuppliers = supplierDataList.size();
            logger.info("供应商导入完成：总耗时 {}ms，总计 {} 条，成功 {} 条，失败 {} 条",
                    totalDuration, totalSuppliers, successCount.get(), totalSuppliers - successCount.get());
            
            return new SupplierImportResponse(
                    new SupplierImportResponse.SupplierImportResult(
                            totalSuppliers,
                            successCount.get(),
                            totalSuppliers - successCount.get(),
                            new ArrayList<>(errors))
            );
        }
        
        private Map<String, Supplier> preloadExistingSuppliers() {
            Set<String> codes = new HashSet<>();
            for (SupplierData data : supplierDataList) {
                codes.add(data.code);
            }
            
            Map<String, Supplier> existingSupplierMap = new HashMap<>();
            List<String> codeList = new ArrayList<>(codes);
            for (int i = 0; i < codeList.size(); i += 1000) {
                int end = Math.min(i + 1000, codeList.size());
                List<String> chunk = codeList.subList(i, end);
                List<Supplier> existingSuppliers = supplierRepository.findByCodeIn(chunk);
                for (Supplier supplier : existingSuppliers) {
                    existingSupplierMap.put(supplier.getCode(), supplier);
                }
            }
            
            return existingSupplierMap;
        }
        
        private int importBatchSuppliers(
                List<SupplierData> batch,
                Map<String, Supplier> existingSupplierMap,
                List<SupplierImportResponse.ImportError> errors) {
            
            int successCount = 0;
            
            for (SupplierData data : batch) {
                try {
                    // 验证编码和名称
                    if (data.code == null || data.code.trim().isEmpty()) {
                        if (errors.size() < MAX_ERROR_COUNT) {
                            errors.add(new SupplierImportResponse.ImportError(
                                    "供应商", data.rowNumber, "编码",
                                    "编码为空"));
                        }
                        continue;
                    }
                    
                    if (data.name == null || data.name.trim().isEmpty()) {
                        if (errors.size() < MAX_ERROR_COUNT) {
                            errors.add(new SupplierImportResponse.ImportError(
                                    "供应商", data.rowNumber, "名称",
                                    "名称为空"));
                        }
                        continue;
                    }
                    
                    // 使用insertOrGetByCode方法，如果存在则更新，不存在则创建
                    try {
                        supplierRepository.insertOrGetByCode(
                                data.code,
                                data.name,
                                data.shortName,
                                data.englishName,
                                data.description
                        );
                        successCount++;
                    } catch (Exception e) {
                        logger.error("保存供应商失败: {}", data.code, e);
                        if (errors.size() < MAX_ERROR_COUNT) {
                            errors.add(new SupplierImportResponse.ImportError(
                                    "供应商", data.rowNumber, "编码",
                                    "保存失败: " + e.getMessage()));
                        }
                    }
                } catch (Exception e) {
                    logger.error("处理供应商失败，行号: {}", data.rowNumber, e);
                    if (errors.size() < MAX_ERROR_COUNT) {
                        errors.add(new SupplierImportResponse.ImportError(
                                "供应商", data.rowNumber, null,
                                "处理失败: " + e.getMessage()));
                    }
                }
            }
            
            return successCount;
        }
    }
    
    // 内部数据类
    private record SupplierData(
            int rowNumber,
            String code,
            String name,
            String shortName,
            String englishName,
            String description
    ) {}
    
    // 批次处理结果
    private record BatchResult(
            int successCount,
            List<SupplierImportResponse.ImportError> errors
    ) {}
}

