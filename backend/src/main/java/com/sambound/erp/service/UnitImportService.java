package com.sambound.erp.service;

import cn.idev.excel.FastExcel;
import cn.idev.excel.context.AnalysisContext;
import cn.idev.excel.read.listener.ReadListener;
import com.sambound.erp.dto.UnitExcelRow;
import com.sambound.erp.dto.UnitImportResponse;
import com.sambound.erp.entity.Unit;
import com.sambound.erp.entity.UnitGroup;
import com.sambound.erp.repository.UnitRepository;
import com.sambound.erp.repository.UnitGroupRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class UnitImportService {

    private static final Logger logger = LoggerFactory.getLogger(UnitImportService.class);

    // 批次大小：每批处理100条记录
    private static final int BATCH_SIZE = 100;
    
    private final UnitService unitService;
    private final UnitRepository unitRepository;
    private final UnitGroupRepository unitGroupRepository;
    private final TransactionTemplate transactionTemplate;
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
        // 创建TransactionTemplate用于程序式事务管理
        // 使用 PROPAGATION_REQUIRES_NEW 确保独立事务，避免嵌套事务问题
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.transactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.transactionTemplate.setTimeout(30); // 30秒超时，防止长时间占用连接
        // 使用虚拟线程执行器（Java 25特性）
        this.executorService = Executors.newVirtualThreadPerTaskExecutor();
    }

    public UnitImportResponse importFromExcel(MultipartFile file) {
        long startTime = System.currentTimeMillis();
        String fileName = file.getOriginalFilename();
        long fileSize = file.getSize();
        
        logger.info("开始导入Excel文件 - 文件名: {}, 大小: {} bytes", fileName, fileSize);
        
        try {
            // 缓存文件内容，以支持多次读取
            long cacheStartTime = System.currentTimeMillis();
            byte[] fileBytes = file.getBytes();
            logger.debug("文件内容缓存完成，耗时: {} ms", System.currentTimeMillis() - cacheStartTime);
            
            // 第一遍读取：收集所有唯一的单位组编码
            logger.info("开始第一遍读取：收集单位组信息");
            long collectStartTime = System.currentTimeMillis();
            UnitGroupCollector collector = new UnitGroupCollector();
            FastExcel.read(new ByteArrayInputStream(fileBytes), UnitExcelRow.class, collector)
                    .sheet()
                    .headRowNumber(2)  // 前两行为表头
                    .doRead();
            logger.info("第一遍读取完成，耗时: {} ms", System.currentTimeMillis() - collectStartTime);
            
            // 预加载单位组到数据库
            logger.info("开始预加载单位组到数据库");
            long preloadStartTime = System.currentTimeMillis();
            Map<String, UnitGroup> unitGroupCache = collector.preloadAndCache();
            logger.info("预加载完成 - 预加载了 {} 个单位组，耗时: {} ms", 
                    unitGroupCache.size(), System.currentTimeMillis() - preloadStartTime);
            
            // 第二遍读取：导入单位数据
            logger.info("开始第二遍读取：导入单位数据");
            long importStartTime = System.currentTimeMillis();
            UnitDataImporter importer = new UnitDataImporter(unitGroupCache);
            FastExcel.read(new ByteArrayInputStream(fileBytes), UnitExcelRow.class, importer)
                    .sheet()
                    .headRowNumber(2)  // 前两行为表头
                    .doRead();
            logger.info("第二遍读取完成，耗时: {} ms", System.currentTimeMillis() - importStartTime);
            
            // 等待所有异步批次处理完成
            logger.info("等待所有批次处理完成...");
            importer.waitForCompletion();
            
            UnitImportResponse result = importer.getResult();
            long totalTime = System.currentTimeMillis() - startTime;
            
            logger.info("Excel文件导入完成 - 总耗时: {} ms, 总记录数: {}, 成功: {}, 失败: {}",
                    totalTime, result.getTotal(), result.getSuccess(), result.getFailure());
            
            return result;
        } catch (Exception e) {
            long totalTime = System.currentTimeMillis() - startTime;
            logger.error("Excel文件导入失败 - 文件名: {}, 耗时: {} ms", fileName, totalTime, e);
            throw new RuntimeException("Excel文件导入失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 单位组收集器（第一遍读取）
     */
    private class UnitGroupCollector implements ReadListener<UnitExcelRow> {
        private final Map<String, String> unitGroupMap = new HashMap<>();
        private final AtomicInteger totalRows = new AtomicInteger(0);
        private static final int LOG_INTERVAL = 1000; // 每处理1000行记录一次进度
        
        @Override
        public void invoke(UnitExcelRow data, AnalysisContext context) {
            int currentRow = totalRows.incrementAndGet();
            
            // 每处理一定数量行记录一次进度
            if (currentRow % LOG_INTERVAL == 0) {
                logger.debug("正在收集单位组信息，已处理 {} 行，已收集 {} 个单位组", 
                        currentRow, unitGroupMap.size());
            }
            
            String unitGroupCode = data.getUnitGroupCode();
            String unitGroupName = data.getUnitGroupName();
            
            if (unitGroupCode != null && !unitGroupCode.trim().isEmpty()) {
                String code = unitGroupCode.trim();
                String name = unitGroupName != null && !unitGroupName.trim().isEmpty() 
                        ? unitGroupName.trim() 
                        : code;
                // 如果同一个编码有多个名称，保留第一个遇到的名称
                boolean isNew = unitGroupMap.putIfAbsent(code, name) == null;
                if (isNew) {
                    logger.debug("收集到新单位组 - 编码: {}, 名称: {}", code, name);
                }
            } else {
                logger.debug("第 {} 行数据缺少单位组编码，跳过", currentRow);
            }
        }
        
        @Override
        public void doAfterAllAnalysed(AnalysisContext context) {
            logger.info("第一遍读取完成，共 {} 行数据，收集到 {} 个唯一的单位组", 
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
            
            logger.info("开始预加载 {} 个单位组到数据库", unitGroupMap.size());
            
            // 在独立事务中创建所有单位组并提交
            long insertStartTime = System.currentTimeMillis();
            transactionTemplate.execute(status -> {
                for (Map.Entry<String, String> entry : unitGroupMap.entrySet()) {
                    try {
                        unitGroupRepository.insertOrGetByCode(entry.getKey(), entry.getValue());
                    } catch (Exception e) {
                        logger.warn("预加载单位组失败 - 编码: {}, 名称: {}, 错误: {}", 
                                entry.getKey(), entry.getValue(), e.getMessage());
                    }
                }
                return null;
            });
            logger.debug("单位组数据库插入完成，耗时: {} ms", System.currentTimeMillis() - insertStartTime);
            
            // 事务提交后，重新从数据库查询所有单位组到缓存
            Map<String, UnitGroup> cache = new ConcurrentHashMap<>();
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount = new AtomicInteger(0);
            
            TransactionTemplate readOnlyTemplate = new TransactionTemplate(transactionManager);
            readOnlyTemplate.setReadOnly(true);
            readOnlyTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            
            long queryStartTime = System.currentTimeMillis();
            readOnlyTemplate.execute(readStatus -> {
                for (String code : unitGroupMap.keySet()) {
                    unitGroupRepository.findByCode(code).ifPresent(unitGroup -> {
                        if (unitGroup.getId() != null) {
                            cache.put(code, unitGroup);
                            successCount.incrementAndGet();
                            logger.debug("成功缓存单位组 - 编码: {}, ID: {}", code, unitGroup.getId());
                        } else {
                            failCount.incrementAndGet();
                            logger.warn("单位组 {} 查询结果没有ID，跳过缓存", code);
                        }
                    });
                    
                    // 如果单位组不存在，记录警告
                    if (!cache.containsKey(code)) {
                        failCount.incrementAndGet();
                        logger.warn("单位组 {} 在数据库中未找到，可能插入失败", code);
                    }
                }
                return null;
            });
            logger.debug("单位组缓存查询完成，耗时: {} ms", System.currentTimeMillis() - queryStartTime);
            
            logger.info("单位组预加载统计 - 总数: {}, 成功缓存: {}, 失败: {}", 
                    unitGroupMap.size(), successCount.get(), failCount.get());
            
            return cache;
        }
    }
    
    /**
     * 单位数据导入器（第二遍读取）
     */
    private class UnitDataImporter implements ReadListener<UnitExcelRow> {
        private final Map<String, UnitGroup> unitGroupCache;
        private final List<UnitExcelRow> batch = new ArrayList<>();
        private final List<CompletableFuture<BatchResult>> futures = new ArrayList<>();
        private final AtomicInteger successCount = new AtomicInteger(0);
        private final AtomicInteger totalRows = new AtomicInteger(0);
        private final AtomicInteger batchIndex = new AtomicInteger(0);
        private final List<UnitImportResponse.ImportError> errors = Collections.synchronizedList(new ArrayList<>());
        private static final int LOG_INTERVAL = 500; // 每处理500行记录一次进度
        
        public UnitDataImporter(Map<String, UnitGroup> unitGroupCache) {
            this.unitGroupCache = unitGroupCache;
            logger.info("单位数据导入器初始化完成，单位组缓存大小: {}", unitGroupCache.size());
        }
        
        @Override
        public void invoke(UnitExcelRow data, AnalysisContext context) {
            int currentRow = totalRows.incrementAndGet();
            
            // 每处理一定数量行记录一次进度
            if (currentRow % LOG_INTERVAL == 0) {
                logger.debug("正在读取单位数据，已处理 {} 行，已提交 {} 个批次", 
                        currentRow, batchIndex.get());
            }
            
            // 累积批次
            batch.add(data);
            if (batch.size() >= BATCH_SIZE) {
                // 异步提交批次处理任务
                int currentBatchIndex = batchIndex.incrementAndGet();
                logger.debug("批次 {} 已累积 {} 条数据，提交异步处理", currentBatchIndex, batch.size());
                processBatchAsync(new ArrayList<>(batch), currentBatchIndex);
                batch.clear();
            }
        }
        
        @Override
        public void doAfterAllAnalysed(AnalysisContext context) {
            // 处理剩余数据
            if (!batch.isEmpty()) {
                int currentBatchIndex = batchIndex.incrementAndGet();
                logger.info("第二遍读取完成，处理最后一个批次 {}，包含 {} 条数据", 
                        currentBatchIndex, batch.size());
                processBatchAsync(new ArrayList<>(batch), currentBatchIndex);
                batch.clear();
            } else {
                logger.info("第二遍读取完成，所有数据已提交批次处理");
            }
            logger.info("共提交了 {} 个批次进行异步处理", batchIndex.get());
            // 注意：等待操作在外部调用 waitForCompletion() 方法执行
        }
        
        private void processBatchAsync(List<UnitExcelRow> batchData, int batchIndex) {
            // 异步提交批次处理任务到线程池
            CompletableFuture<BatchResult> future = CompletableFuture.supplyAsync(() -> {
                return processBatch(batchData, batchIndex);
            }, executorService);
            futures.add(future);
            logger.debug("批次 {} 已提交到线程池，等待处理", batchIndex);
        }
        
        private BatchResult processBatch(List<UnitExcelRow> batch, int batchIndex) {
            long batchStartTime = System.currentTimeMillis();
            logger.debug("开始处理批次 {}，包含 {} 条数据", batchIndex, batch.size());
            
            List<UnitImportResponse.ImportError> batchErrors = new ArrayList<>();
            AtomicInteger batchSuccessCount = new AtomicInteger(0);
            
            // 每个批次在独立事务中处理
            transactionTemplate.execute(status -> {
                int rowIndex = 0;
                for (UnitExcelRow data : batch) {
                    rowIndex++;
                    try {
                        importUnitRow(data, unitGroupCache);
                        batchSuccessCount.incrementAndGet();
                    } catch (Exception e) {
                        String unitCode = data.getCode() != null ? data.getCode() : "未知";
                        logger.warn("批次 {} 第 {} 条数据导入失败 - 单位编码: {}, 错误: {}", 
                                batchIndex, rowIndex, unitCode, e.getMessage());
                        batchErrors.add(new UnitImportResponse.ImportError(
                                unitCode, 
                                data.getName(), 
                                e.getMessage()));
                    }
                }
                return null;
            });
            
            long batchTime = System.currentTimeMillis() - batchStartTime;
            int success = batchSuccessCount.get();
            int failure = batchErrors.size();
            
            logger.info("批次 {} 处理完成 - 耗时: {} ms, 成功: {}, 失败: {}", 
                    batchIndex, batchTime, success, failure);
            
            if (!batchErrors.isEmpty()) {
                logger.debug("批次 {} 错误详情: {}", batchIndex, 
                        batchErrors.stream()
                                .map(e -> String.format("[%s: %s]", e.getCode(), e.getMessage()))
                                .reduce((a, b) -> a + ", " + b)
                                .orElse("无"));
            }
            
            return new BatchResult(success, batchErrors);
        }
        
        /**
         * 等待所有异步批次处理完成
         */
        public void waitForCompletion() {
            if (futures.isEmpty()) {
                logger.info("没有需要处理的批次");
                return;
            }

            logger.info("开始等待 {} 个批次处理完成，超时时间: 10 分钟", futures.size());
            long waitStartTime = System.currentTimeMillis();

            try {
                // 等待所有批次完成（最多10分钟）
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                        .get(10, TimeUnit.MINUTES);
                
                long waitTime = System.currentTimeMillis() - waitStartTime;
                logger.info("所有批次处理完成，等待耗时: {} ms", waitTime);
                
                // 收集所有批次的结果
                collectResults();
            } catch (TimeoutException e) {
                long waitTime = System.currentTimeMillis() - waitStartTime;
                int completedCount = (int) futures.stream().filter(CompletableFuture::isDone).count();
                logger.error("导入超时 - 等待耗时: {} ms, 已完成批次: {}/{}", 
                        waitTime, completedCount, futures.size(), e);
                // 取消未完成的任务
                futures.forEach(f -> {
                    if (!f.isDone()) {
                        f.cancel(true);
                        logger.warn("已取消未完成的批次任务");
                    }
                });
                throw new RuntimeException("导入超时，请检查数据量或重试", e);
            } catch (Exception e) {
                long waitTime = System.currentTimeMillis() - waitStartTime;
                logger.error("批次处理失败 - 等待耗时: {} ms", waitTime, e);
                // 取消未完成的任务
                futures.forEach(f -> {
                    if (!f.isDone()) {
                        f.cancel(true);
                        logger.warn("已取消未完成的批次任务");
                    }
                });
                throw new RuntimeException("导入失败: " + e.getMessage(), e);
            }
        }

        /**
         * 收集所有批次的处理结果
         */
        private void collectResults() {
            logger.info("开始收集所有批次的处理结果");
            int batchIndex = 0;
            int collectedCount = 0;
            int cancelledCount = 0;
            int errorCount = 0;
            
            for (CompletableFuture<BatchResult> future : futures) {
                batchIndex++;
                try {
                    BatchResult result = future.get();
                    successCount.addAndGet(result.successCount());
                    errors.addAll(result.errors());
                    collectedCount++;
                    logger.debug("批次 {} 结果已收集 - 成功: {}, 累计成功: {}", 
                            batchIndex, result.successCount(), successCount.get());
                } catch (CancellationException e) {
                    cancelledCount++;
                    logger.warn("批次 {} 被取消，跳过结果收集", batchIndex);
                } catch (Exception e) {
                    errorCount++;
                    logger.error("获取批次 {} 处理结果失败", batchIndex, e);
                }
            }
            
            logger.info("批次结果收集完成 - 总数: {}, 已收集: {}, 已取消: {}, 收集错误: {}", 
                    futures.size(), collectedCount, cancelledCount, errorCount);
        }
        
        public UnitImportResponse getResult() {
            int total = totalRows.get();
            int success = successCount.get();
            int failure = total - success;
            
            logger.info("生成导入结果 - 总记录数: {}, 成功: {}, 失败: {}, 错误详情数: {}", 
                    total, success, failure, errors.size());
            
            if (failure > 0) {
                logger.warn("导入过程中存在失败记录，失败数量: {}", failure);
                // 只记录前10个错误详情，避免日志过长
                int logErrorCount = Math.min(10, errors.size());
                for (int i = 0; i < logErrorCount; i++) {
                    UnitImportResponse.ImportError error = errors.get(i);
                    logger.warn("错误 {}: 单位编码={}, 单位名称={}, 错误信息={}", 
                            i + 1, error.getCode(), error.getName(), error.getMessage());
                }
                if (errors.size() > 10) {
                    logger.warn("... 还有 {} 个错误未显示", errors.size() - 10);
                }
            }
            
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
            logger.warn("单位编码为空，跳过导入");
            throw new IllegalArgumentException("单位编码不能为空");
        }
        if (unitName == null || unitName.trim().isEmpty()) {
            logger.warn("单位名称为空 - 单位编码: {}, 跳过导入", unitCode);
            throw new IllegalArgumentException("单位名称不能为空");
        }
        if (unitGroupCode == null || unitGroupCode.trim().isEmpty()) {
            logger.warn("单位组编码为空 - 单位编码: {}, 单位名称: {}, 跳过导入", 
                    unitCode, unitName);
            throw new IllegalArgumentException("单位组编码不能为空");
        }

        // 从缓存中获取单位组
        String code = unitGroupCode.trim();
        UnitGroup unitGroup = unitGroupCache.get(code);
        
        if (unitGroup == null) {
            logger.error("单位组未找到 - 单位编码: {}, 单位名称: {}, 单位组编码: {}, 可用单位组数: {}", 
                    unitCode, unitName, code, unitGroupCache.size());
            throw new IllegalStateException("单位组未预加载，请确保单位组编码正确: " + code);
        }

        // 确保 UnitGroup 已经持久化并分配了 ID
        if (unitGroup.getId() == null) {
            logger.error("单位组ID为空 - 单位编码: {}, 单位组编码: {}", unitCode, unitGroupCode);
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

        logger.debug("单位处理成功 - 编码: {}, 名称: {}, ID: {}, 单位组: {}", 
                unitCode, unitName, unit.getId(), unitGroupCode);

        // 如果有转换率信息，直接设置到单位实体
        if (numeratorStr != null && !numeratorStr.trim().isEmpty()) {
            try {
                BigDecimal numerator = parseDecimal(numeratorStr);
                BigDecimal denominator = denominatorStr != null && !denominatorStr.trim().isEmpty()
                        ? parseDecimal(denominatorStr)
                        : BigDecimal.ONE;

                if (denominator.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new IllegalArgumentException("换算分母必须大于0: " + denominator);
                }

                // 直接将转换率设置到单位实体（相对于单位组的转换率）
                unit.setNumerator(numerator);
                unit.setDenominator(denominator);
                unitRepository.save(unit);
                logger.debug("单位 {} 转换率设置成功 - 分子: {}, 分母: {}", 
                        unitCode, numerator, denominator);
            } catch (NumberFormatException e) {
                logger.warn("单位 {} 的转换率格式错误 - 分子: {}, 分母: {}, 错误: {}", 
                        unitCode, numeratorStr, denominatorStr, e.getMessage());
                // 转换率格式错误不影响单位导入，只记录警告
            } catch (IllegalArgumentException e) {
                logger.warn("单位 {} 的转换率验证失败 - 分子: {}, 分母: {}, 错误: {}", 
                        unitCode, numeratorStr, denominatorStr, e.getMessage());
                // 转换率验证错误不影响单位导入，只记录警告
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