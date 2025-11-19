package com.sambound.erp.service.importing.unit;

import cn.idev.excel.FastExcel;
import cn.idev.excel.context.AnalysisContext;
import cn.idev.excel.read.listener.ReadListener;
import com.sambound.erp.config.ImportConfiguration;
import com.sambound.erp.dto.UnitImportResponse;
import com.sambound.erp.entity.Unit;
import com.sambound.erp.entity.UnitGroup;
import com.sambound.erp.repository.UnitGroupRepository;
import com.sambound.erp.repository.UnitRepository;
import com.sambound.erp.service.UnitService;
import com.sambound.erp.service.importing.dto.UnitExcelRow;
import com.sambound.erp.service.importing.exception.ImportProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class UnitImportProcessor {

    private static final Logger logger = LoggerFactory.getLogger(UnitImportProcessor.class);

    private final UnitService unitService;
    private final UnitRepository unitRepository;
    private final UnitGroupRepository unitGroupRepository;
    private final PlatformTransactionManager transactionManager;
    private final TransactionTemplate transactionTemplate;
    private final ExecutorService executorService;
    private final ImportConfiguration importConfig;

    public UnitImportProcessor(
            UnitService unitService,
            UnitRepository unitRepository,
            UnitGroupRepository unitGroupRepository,
            PlatformTransactionManager transactionManager,
            ExecutorService executorService,
            ImportConfiguration importConfig) {
        this.unitService = unitService;
        this.unitRepository = unitRepository;
        this.unitGroupRepository = unitGroupRepository;
        this.transactionManager = transactionManager;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.transactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.transactionTemplate.setTimeout(importConfig.getTimeout().getTransactionTimeoutSeconds());
        this.executorService = executorService;
        this.importConfig = importConfig;
    }

    public UnitImportResponse process(byte[] fileBytes, String fileName) {
        try {
            // 第一遍读取：收集所有唯一的单位组编码
            UnitGroupCollector collector = new UnitGroupCollector();
            FastExcel.read(new ByteArrayInputStream(fileBytes), UnitExcelRow.class, collector)
                    .sheet()
                    .headRowNumber(2)
                    .doRead();
            
            // 预加载单位组到数据库
            Map<String, UnitGroup> unitGroupCache = collector.preloadAndCache();
            logger.info("预加载了 {} 个单位组", unitGroupCache.size());
            
            // 第二遍读取：导入单位数据
            UnitDataImporter importer = new UnitDataImporter(unitGroupCache, importConfig);
            FastExcel.read(new ByteArrayInputStream(fileBytes), UnitExcelRow.class, importer)
                    .sheet()
                    .headRowNumber(2)
                    .doRead();
            
            // 等待所有异步批次处理完成
            importer.waitForCompletion();
            
            UnitImportResponse result = importer.getResult();
            logger.info("单位导入完成 [{}]: 总计 {} 条，成功 {} 条，失败 {} 条",
                    fileName, result.totalRows(), result.successCount(), result.failureCount());
            return result;
        } catch (Exception e) {
            logger.error("单位Excel导入失败", e);
            throw new ImportProcessingException("单位Excel导入失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 单位组收集器（第一遍读取）
     */
    private class UnitGroupCollector implements ReadListener<UnitExcelRow> {
        private final Map<String, String> unitGroupMap = new HashMap<>();
        private final AtomicInteger totalRows = new AtomicInteger(0);
        
        @Override
        public void invoke(UnitExcelRow data, AnalysisContext context) {
            totalRows.incrementAndGet();
            
            String unitGroupCode = data.getUnitGroupCode();
            String unitGroupName = data.getUnitGroupName();
            
            if (unitGroupCode != null && !unitGroupCode.trim().isEmpty()) {
                String code = unitGroupCode.trim();
                String name = unitGroupName != null && !unitGroupName.trim().isEmpty() 
                        ? unitGroupName.trim() 
                        : code;
                // 如果同一个编码有多个名称，保留第一个遇到的名称
                unitGroupMap.putIfAbsent(code, name);
            }
        }
        
        @Override
        public void doAfterAllAnalysed(AnalysisContext context) {
            logger.info("第一遍读取完成，共 {} 行数据，收集到 {} 个单位组", 
                    totalRows.get(), unitGroupMap.size());
        }
        
        /**
         * 预加载单位组到数据库并缓存
         */
        public Map<String, UnitGroup> preloadAndCache() {
            if (unitGroupMap.isEmpty()) {
                logger.info("没有需要预加载的单位组");
                return new HashMap<>();
            }
            
            // 在独立事务中创建所有单位组并提交
            transactionTemplate.execute(status -> {
                for (Map.Entry<String, String> entry : unitGroupMap.entrySet()) {
                    try {
                        unitGroupRepository.insertOrGetByCode(entry.getKey(), entry.getValue());
                    } catch (Exception e) {
                        logger.warn("预加载单位组失败: {} - {}", entry.getKey(), e.getMessage());
                    }
                }
                return null;
            });
            
            // 事务提交后，重新从数据库查询所有单位组到缓存
            Map<String, UnitGroup> cache = new ConcurrentHashMap<>();
            
            TransactionTemplate readOnlyTemplate = new TransactionTemplate(transactionManager);
            readOnlyTemplate.setReadOnly(true);
            readOnlyTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            
            readOnlyTemplate.execute(readStatus -> {
                for (String code : unitGroupMap.keySet()) {
                    unitGroupRepository.findByCode(code).ifPresent(unitGroup -> {
                        if (unitGroup.getId() != null) {
                            cache.put(code, unitGroup);
                        } else {
                            logger.warn("单位组 {} 查询结果没有ID，跳过", code);
                        }
                    });
                }
                return null;
            });
            
            return cache;
        }
    }
    
    /**
     * 单位数据导入器（第二遍读取）
     */
    private class UnitDataImporter implements ReadListener<UnitExcelRow> {
        private final Map<String, UnitGroup> unitGroupCache;
        private final ImportConfiguration importConfig;
        private final List<UnitExcelRow> batch = new ArrayList<>();
        private final List<CompletableFuture<BatchResult>> futures = new ArrayList<>();
        private final AtomicInteger successCount = new AtomicInteger(0);
        private final AtomicInteger totalRows = new AtomicInteger(0);
        private final List<UnitImportResponse.ImportError> errors = Collections.synchronizedList(new ArrayList<>());
        private final Semaphore semaphore;
        
        public UnitDataImporter(Map<String, UnitGroup> unitGroupCache, ImportConfiguration importConfig) {
            this.unitGroupCache = unitGroupCache;
            this.importConfig = importConfig;
            this.semaphore = new Semaphore(importConfig.getConcurrency().getMaxConcurrentBatches());
        }
        
        @Override
        public void invoke(UnitExcelRow data, AnalysisContext context) {
            totalRows.incrementAndGet();
            
            // 累积批次
            batch.add(data);
            if (batch.size() >= importConfig.getBatch().getInsertSize()) {
                // 异步提交批次处理任务
                processBatchAsync(new ArrayList<>(batch));
                batch.clear();
            }
        }
        
        @Override
        public void doAfterAllAnalysed(AnalysisContext context) {
            // 处理剩余数据
            if (!batch.isEmpty()) {
                processBatchAsync(new ArrayList<>(batch));
            }
            // 注意：等待操作在外部调用 waitForCompletion() 方法执行
        }
        
        private void processBatchAsync(List<UnitExcelRow> batchData) {
            // 异步提交批次处理任务到线程池
            CompletableFuture<BatchResult> future = CompletableFuture.supplyAsync(() -> {
                try {
                    semaphore.acquire();
                    try {
                        return processBatch(batchData);
                    } finally {
                        semaphore.release();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return new BatchResult(0, List.of(new UnitImportResponse.ImportError(null, null, "批次处理被中断")));
                }
            }, executorService);
            futures.add(future);
        }
        
        private BatchResult processBatch(List<UnitExcelRow> batch) {
            List<UnitImportResponse.ImportError> batchErrors = new ArrayList<>();
            AtomicInteger batchSuccessCount = new AtomicInteger(0);
            
            // 每个批次在独立事务中处理
            transactionTemplate.execute(status -> {
                for (UnitExcelRow data : batch) {
                    try {
                        importUnitRow(data, unitGroupCache);
                        batchSuccessCount.incrementAndGet();
                    } catch (Exception e) {
                        logger.warn("导入单位数据失败: {}", e.getMessage());
                        if (batchErrors.size() < importConfig.getError().getMaxErrorCount()) {
                            batchErrors.add(new UnitImportResponse.ImportError(null, null, e.getMessage()));
                        }
                    }
                }
                return null;
            });
            
            return new BatchResult(batchSuccessCount.get(), batchErrors);
        }
        
        /**
         * 等待所有异步批次处理完成
         */
        public void waitForCompletion() {
            if (futures.isEmpty()) {
                logger.info("没有需要处理的批次");
                return;
            }

            try {
                // 等待所有批次完成
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                        .get(importConfig.getTimeout().getProcessingTimeoutMinutes(), TimeUnit.MINUTES);
                
                // 收集所有批次的结果
                collectResults();
            } catch (TimeoutException e) {
                logger.error("导入超时", e);
                // 取消未完成的任务
                futures.forEach(f -> f.cancel(true));
                throw new ImportProcessingException("导入超时，请检查数据量或重试", e);
            } catch (Exception e) {
                logger.error("批次处理失败", e);
                // 取消未完成的任务
                futures.forEach(f -> f.cancel(true));
                throw new ImportProcessingException("导入失败: " + e.getMessage(), e);
            }
        }

        /**
         * 收集所有批次的处理结果
         */
        private void collectResults() {
            for (CompletableFuture<BatchResult> future : futures) {
                try {
                    BatchResult result = future.get();
                    successCount.addAndGet(result.successCount());
                    if (errors.size() < importConfig.getError().getMaxErrorCount()) {
                        errors.addAll(result.errors());
                    }
                } catch (CancellationException e) {
                    logger.warn("批次被取消");
                } catch (Exception e) {
                    logger.error("获取批次处理结果失败", e);
                }
            }
        }
        
        public UnitImportResponse getResult() {
            int total = totalRows.get();
            int success = successCount.get();
            int failure = total - success;
            return new UnitImportResponse(total, success, failure, new ArrayList<>(errors));
        }
    }
    
    /**
     * 导入单位行数据
     */
    private void importUnitRow(UnitExcelRow data, Map<String, UnitGroup> unitGroupCache) {
        // 获取字段值
        String unitCode = data.getCode();
        String unitName = data.getName();
        String unitGroupCode = data.getUnitGroupCode();
        String numeratorStr = data.getNumerator();
        String denominatorStr = data.getDenominator();

        // 验证必填字段
        if (unitCode == null || unitCode.trim().isEmpty()) {
            throw new IllegalArgumentException("单位编码不能为空");
        }
        if (unitName == null || unitName.trim().isEmpty()) {
            throw new IllegalArgumentException("单位名称不能为空");
        }
        if (unitGroupCode == null || unitGroupCode.trim().isEmpty()) {
            throw new IllegalArgumentException("单位组编码不能为空");
        }

        // 从缓存中获取单位组
        String code = unitGroupCode.trim();
        UnitGroup unitGroup = unitGroupCache.get(code);
        
        if (unitGroup == null) {
            throw new IllegalStateException("单位组未预加载，请确保单位组编码正确: " + code);
        }

        // 确保 UnitGroup 已经持久化并分配了 ID
        if (unitGroup.getId() == null) {
            throw new IllegalStateException("单位组未正确保存: " + unitGroupCode);
        }

        // 创建或获取单位
        Unit unit = unitService.findOrCreateByCode(
                unitCode.trim(),
                unitName.trim(),
                unitGroup
        );

        // 确保 Unit 已经持久化并分配了 ID
        if (unit.getId() == null) {
            throw new IllegalStateException("单位未正确保存: " + unitCode);
        }

        // 如果有转换率信息，直接设置到单位实体
        if (numeratorStr != null && !numeratorStr.trim().isEmpty()) {
            try {
                BigDecimal numerator = parseDecimal(numeratorStr);
                BigDecimal denominator = denominatorStr != null && !denominatorStr.trim().isEmpty()
                        ? parseDecimal(denominatorStr)
                        : BigDecimal.ONE;

                if (denominator.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new IllegalArgumentException("换算分母必须大于0");
                }

                // 直接将转换率设置到单位实体（相对于单位组的转换率）
                unit.setNumerator(numerator);
                unit.setDenominator(denominator);
                unitRepository.save(unit);
            } catch (NumberFormatException e) {
                logger.warn("单位 {} 的转换率格式错误: {}", unitCode, e.getMessage());
                // 转换率格式错误不影响单位导入，只记录警告
            }
        }
    }
    
    /**
     * 解析数值字符串，移除千位分隔符和其他非数字字符（保留小数点和负号）
     */
    private BigDecimal parseDecimal(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new NumberFormatException("数值字符串为空");
        }
        
        // 移除空格、千位分隔符（逗号）和其他非数字字符（保留小数点、负号、E/e用于科学计数法）
        String cleaned = value.trim()
                .replaceAll("\\s+", "")  // 移除所有空格
                .replaceAll("[,，]", ""); // 移除千位分隔符（中文和英文逗号）
        
        try {
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            logger.warn("无法解析数值: {}", value);
            throw new NumberFormatException("无法解析数值: " + value + "，清理后为: " + cleaned);
        }
    }
    
    /**
     * 批次处理结果
     */
    private record BatchResult(
            int successCount,
            List<UnitImportResponse.ImportError> errors
    ) {}
}