package com.sambound.erp.service.importing.unit;

import cn.idev.excel.FastExcel;
import cn.idev.excel.context.AnalysisContext;
import cn.idev.excel.read.listener.ReadListener;
import com.sambound.erp.dto.UnitImportResponse;
import com.sambound.erp.entity.Unit;
import com.sambound.erp.entity.UnitGroup;
import com.sambound.erp.repository.UnitGroupRepository;
import com.sambound.erp.repository.UnitRepository;
import com.sambound.erp.service.UnitService;
import com.sambound.erp.service.importing.dto.UnitExcelRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class UnitImportProcessor {

    private static final Logger logger = LoggerFactory.getLogger(UnitImportProcessor.class);

    // 批次大小：每批处理100条记录
    private static final int BATCH_SIZE = 100;
    // 流式处理时的批次大小，达到此大小时立即处理
    private static final int STREAM_BATCH_SIZE = 200;

    private final UnitService unitService;
    private final UnitRepository unitRepository;
    private final UnitGroupRepository unitGroupRepository;
    private final PlatformTransactionManager transactionManager;
    private final TransactionTemplate transactionTemplate;
    private final ExecutorService executorService;

    public UnitImportProcessor(
            UnitService unitService,
            UnitRepository unitRepository,
            UnitGroupRepository unitGroupRepository,
            PlatformTransactionManager transactionManager,
            ExecutorService executorService) {
        this.unitService = unitService;
        this.unitRepository = unitRepository;
        this.unitGroupRepository = unitGroupRepository;
        this.transactionManager = transactionManager;
        // 创建TransactionTemplate用于程序式事务管理
        // 使用 PROPAGATION_REQUIRES_NEW 确保独立事务，避免嵌套事务问题
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.transactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.transactionTemplate.setTimeout(30); // 30秒超时
        this.executorService = executorService;
    }

    /**
     * 从输入流处理导入（流式分批处理）
     * 先收集单位组编码并预加载，然后流式处理单位数据
     */
    public UnitImportResponse process(InputStream inputStream) {
        CombinedCollector collector = new CombinedCollector();
        try {
            // 使用 CombinedCollector 收集单位组编码和流式处理单位数据
            FastExcel.read(inputStream, UnitExcelRow.class, collector)
                    .sheet()
                    .headRowNumber(2)
                    .doRead();
        } catch (Exception e) {
            logger.error("Excel文件读取失败", e);
            throw new RuntimeException("Excel文件读取失败: " + e.getMessage(), e);
        }

        try {
            // doAfterAllAnalysed 已经处理了预加载和导入器创建
            // 但如果文件为空或格式错误，doAfterAllAnalysed 可能没有被调用
            // 确保 importer 被初始化
            UnitDataImporter importer = collector.getImporter();
            if (importer == null) {
                // 如果没有数据，创建一个空的导入器并返回空结果
                logger.warn("没有数据需要导入，返回空结果");
                Map<String, UnitGroup> emptyCache = new HashMap<>();
                importer = new UnitDataImporter(emptyCache);
            }
            
            importer.waitForCompletion();
            return importer.getResult();
        } catch (Exception e) {
            logger.error("Excel文件导入失败", e);
            throw new RuntimeException("Excel文件导入失败: " + e.getMessage(), e);
        }
    }

    /**
     * 组合收集器：单次读取同时收集单位组编码和单位数据
     * 由于需要先预加载单位组，所以先收集所有数据，然后在 doAfterAllAnalysed 中分批处理
     */
    private class CombinedCollector implements ReadListener<UnitExcelRow> {
        private final Map<String, String> unitGroupMap = new HashMap<>();
        private final List<UnitExcelRow> allRows = new ArrayList<>();
        private final AtomicInteger totalRows = new AtomicInteger(0);
        private UnitDataImporter importer;
        private Map<String, UnitGroup> unitGroupCache;

        @Override
        public void invoke(UnitExcelRow data, AnalysisContext context) {
            totalRows.incrementAndGet();

            // 收集单位组编码
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

            // 收集行数据（由于需要先预加载单位组，所以先收集）
            allRows.add(data);
        }

        @Override
        public void doAfterAllAnalysed(AnalysisContext context) {
            logger.info("读取完成，共 {} 行数据，收集到 {} 个单位组",
                    totalRows.get(), unitGroupMap.size());
            
            // 先预加载单位组
            unitGroupCache = preloadAndCache();
            logger.debug("单位组预加载完成，缓存大小: {}", unitGroupCache.size());
            
            // 创建导入器
            importer = new UnitDataImporter(unitGroupCache);
            logger.debug("导入器已创建");
            
            // 然后分批处理所有数据（流式分批处理）
            if (!allRows.isEmpty()) {
                int batchCount = (allRows.size() + STREAM_BATCH_SIZE - 1) / STREAM_BATCH_SIZE;
                logger.info("开始分批处理 {} 条数据，共 {} 个批次", allRows.size(), batchCount);
                // 分批处理，每批 STREAM_BATCH_SIZE 条
                for (int i = 0; i < allRows.size(); i += STREAM_BATCH_SIZE) {
                    int end = Math.min(i + STREAM_BATCH_SIZE, allRows.size());
                    List<UnitExcelRow> batch = new ArrayList<>(allRows.subList(i, end));
                    importer.processBatchImmediately(batch);
                }
                allRows.clear();
            } else {
                logger.info("没有数据需要处理");
            }
        }

        /**
         * 获取导入器
         */
        public UnitDataImporter getImporter() {
            return importer;
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
     * 单位数据导入器
     */
    private class UnitDataImporter {
        private final Map<String, UnitGroup> unitGroupCache;
        private final List<CompletableFuture<BatchResult>> futures = new ArrayList<>();
        private final AtomicInteger successCount = new AtomicInteger(0);
        private final AtomicInteger totalRows = new AtomicInteger(0);
        private final List<UnitImportResponse.ImportError> errors = Collections.synchronizedList(new ArrayList<>());

        public UnitDataImporter(Map<String, UnitGroup> unitGroupCache) {
            this.unitGroupCache = unitGroupCache;
        }

        /**
         * 立即处理批次（流式处理）
         */
        public void processBatchImmediately(List<UnitExcelRow> batch) {
            if (batch.isEmpty()) {
                return;
            }

            totalRows.addAndGet(batch.size());
            logger.debug("流式处理单位批次，共 {} 条数据", batch.size());
            
            // 异步提交批次处理任务
            processBatchAsync(new ArrayList<>(batch));
        }

        /**
         * 处理所有累积的行数据（兼容方法，已废弃，使用 processBatchImmediately）
         */
        @Deprecated
        public void processAllRows(List<UnitExcelRow> allRows) {
            totalRows.set(allRows.size());
            
            // 按批次处理数据
            List<UnitExcelRow> batch = new ArrayList<>();
            for (UnitExcelRow data : allRows) {
                batch.add(data);
                if (batch.size() >= BATCH_SIZE) {
                    // 异步提交批次处理任务
                    processBatchAsync(new ArrayList<>(batch));
                    batch.clear();
                }
            }
            
            // 处理剩余数据
            if (!batch.isEmpty()) {
                processBatchAsync(new ArrayList<>(batch));
            }
        }

        private void processBatchAsync(List<UnitExcelRow> batchData) {
            // 异步提交批次处理任务到线程池
            CompletableFuture<BatchResult> future = CompletableFuture.supplyAsync(() -> {
                return processBatch(batchData);
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
                        batchErrors.add(new UnitImportResponse.ImportError(null, null, e.getMessage()));
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
                // 等待所有批次完成（最多10分钟）
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                        .get(10, TimeUnit.MINUTES);

                // 收集所有批次的结果
                collectResults();
            } catch (TimeoutException e) {
                logger.error("导入超时", e);
                // 取消未完成的任务
                futures.forEach(f -> f.cancel(true));
                throw new RuntimeException("导入超时，请检查数据量或重试", e);
            } catch (Exception e) {
                logger.error("批次处理失败", e);
                // 取消未完成的任务
                futures.forEach(f -> f.cancel(true));
                throw new RuntimeException("导入失败: " + e.getMessage(), e);
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
                    errors.addAll(result.errors());
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
    ) {
    }
}