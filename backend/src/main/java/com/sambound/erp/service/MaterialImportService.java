package com.sambound.erp.service;

import cn.idev.excel.FastExcel;
import cn.idev.excel.context.AnalysisContext;
import cn.idev.excel.read.listener.ReadListener;
import com.sambound.erp.dto.MaterialExcelRow;
import com.sambound.erp.dto.MaterialGroupExcelRow;
import com.sambound.erp.dto.MaterialImportResponse;
import com.sambound.erp.entity.Material;
import com.sambound.erp.entity.MaterialGroup;
import com.sambound.erp.entity.Unit;
import com.sambound.erp.repository.MaterialGroupRepository;
import com.sambound.erp.repository.MaterialRepository;
import com.sambound.erp.repository.UnitRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class MaterialImportService {

    private static final Logger logger = LoggerFactory.getLogger(MaterialImportService.class);

    // 批次大小：对于10万+数据，使用更大的批次以提高效率
    private static final int MATERIAL_BATCH_SIZE = 1000;
    private static final int MAX_ERROR_COUNT = 1000;
    // 最大并发批次数量：适当增加并发以提高吞吐量
    // 每个批次最多需要2个连接（1个预加载+1个主事务），设置为8可以保证最多16个连接
    // 假设连接池至少有20个连接，这样可以充分利用连接池
    private static final int MAX_CONCURRENT_BATCHES = 8;
    // 批量查询时的分片大小，避免IN查询参数过多（PostgreSQL通常限制为32767）
    private static final int BATCH_QUERY_CHUNK_SIZE = 1000;

    private final MaterialGroupService materialGroupService;
    private final MaterialGroupRepository materialGroupRepository;
    private final MaterialRepository materialRepository;
    private final UnitRepository unitRepository;
    private final TransactionTemplate transactionTemplate;
    private final PlatformTransactionManager transactionManager;

    private final ExecutorService executorService;
    // 信号量用于限制并发批次数量
    private final Semaphore batchSemaphore;
    
    @PersistenceContext
    private EntityManager entityManager;

    public MaterialImportService(
            MaterialGroupService materialGroupService,
            MaterialService materialService,
            MaterialGroupRepository materialGroupRepository,
            MaterialRepository materialRepository,
            UnitRepository unitRepository,
            PlatformTransactionManager transactionManager) {
        this.materialGroupService = materialGroupService;
        this.materialGroupRepository = materialGroupRepository;
        this.materialRepository = materialRepository;
        this.unitRepository = unitRepository;
        this.transactionManager = transactionManager;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.transactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.transactionTemplate.setTimeout(30); // 30秒超时，防止长时间占用连接
        // 使用虚拟线程执行器（Java 25特性）
        this.executorService = Executors.newVirtualThreadPerTaskExecutor();
        // 初始化信号量，限制并发批次数量
        this.batchSemaphore = new Semaphore(MAX_CONCURRENT_BATCHES, true);
    }

    public MaterialImportResponse importFromExcel(MultipartFile file) {
        logger.info("开始导入Excel文件: {}", file.getOriginalFilename());

        try {
            // 缓存文件内容
            byte[] fileBytes = file.getBytes();

            // 处理物料组：第一步，收集数据（不在回调中执行数据库操作，避免连接冲突）
            MaterialGroupCollector groupCollector = new MaterialGroupCollector();
            FastExcel.read(new ByteArrayInputStream(fileBytes), MaterialGroupExcelRow.class, groupCollector)
                    .sheet("数据分组#单据头(FBillHead)Group")
                    .headRowNumber(2)
                    .doRead();

            // 第二步：在回调外部执行数据库导入操作，确保在干净的上下文中执行
            MaterialImportResponse.UnitGroupImportResult unitGroupResult = groupCollector.importToDatabase();
            logger.info("物料组导入完成：总计 {} 条，成功 {} 条，失败 {} 条",
                    unitGroupResult.totalRows(), unitGroupResult.successCount(), unitGroupResult.failureCount());

            // 获取导入的物料组缓存（用于前缀匹配）
            Map<String, MaterialGroup> importedMaterialGroupCache = groupCollector.getImportedMaterialGroupCache();

            // 处理物料，传入导入的物料组缓存
            MaterialDataImporter materialImporter = new MaterialDataImporter(importedMaterialGroupCache);
            FastExcel.read(new ByteArrayInputStream(fileBytes), MaterialExcelRow.class, materialImporter)
                    .sheet("物料#物料(FBillHead)")
                    .headRowNumber(2)
                    .doRead();

            // 等待所有异步批次处理完成
            materialImporter.waitForCompletion();

            MaterialImportResponse.MaterialImportResult materialResult = materialImporter.getResult();
            logger.info("物料导入完成：总计 {} 条，成功 {} 条，失败 {} 条",
                    materialResult.totalRows(), materialResult.successCount(), materialResult.failureCount());

            return new MaterialImportResponse(unitGroupResult, materialResult);
        } catch (Exception e) {
            logger.error("Excel文件导入失败", e);
            throw new RuntimeException("Excel文件导入失败: " + e.getMessage(), e);
        }
    }

    public MaterialGroupService getMaterialGroupService() {
        return materialGroupService;
    }

    /**
     * 物料组收集器：只负责收集数据，不执行数据库操作
     * 在 FastExcel 回调中只做数据收集，避免在回调上下文中执行事务操作
     */
    private class MaterialGroupCollector implements ReadListener<MaterialGroupExcelRow> {
        private final List<MaterialGroupData> materialGroups = new ArrayList<>();
        private final AtomicInteger totalRows = new AtomicInteger(0);
        
        // 缓存已导入的物料组对象（code -> MaterialGroup），用于前缀匹配
        private final Map<String, MaterialGroup> importedMaterialGroupCache = new ConcurrentHashMap<>();

        @Override
        public void invoke(MaterialGroupExcelRow data, AnalysisContext context) {
            totalRows.incrementAndGet();

            int rowNum = context.readRowHolder().getRowIndex();
            String code = data.getCode();
            String name = data.getName();

            if (code == null || code.trim().isEmpty() || name == null || name.trim().isEmpty()) {
                // 在回调中只记录数据，不执行数据库操作
                return;
            }

            materialGroups.add(new MaterialGroupData(rowNum, code.trim(), name.trim(),
                    data.getDescription() != null ? data.getDescription().trim() : null,
                    (data.getParentCode() != null && !data.getParentCode().trim().isEmpty())
                            ? data.getParentCode().trim() : null));
        }

        @Override
        public void doAfterAllAnalysed(AnalysisContext context) {
            // 在回调中只记录日志，不执行数据库操作
            logger.info("物料组数据收集完成，共 {} 条数据", materialGroups.size());
        }

        /**
         * 在回调外部执行数据库导入操作，确保在干净的上下文中执行
         * 使用声明式事务管理，确保事务边界清晰和资源正确释放
         */
        public MaterialImportResponse.UnitGroupImportResult importToDatabase() {
            if (materialGroups.isEmpty()) {
                logger.info("未找到物料组数据");
                return new MaterialImportResponse.UnitGroupImportResult(
                        0, 0, 0, new ArrayList<>());
            }

            logger.info("找到 {} 条物料组数据，开始导入到数据库", materialGroups.size());
            
            List<MaterialImportResponse.ImportError> errors = new ArrayList<>();
            AtomicInteger successCount = new AtomicInteger(0);

            // 处理树形结构：先导入顶级节点，再导入子节点
            // 第一遍：分离顶级节点和子节点
            List<MaterialGroupData> rootNodes = new ArrayList<>();
            List<MaterialGroupData> childNodes = new ArrayList<>();
            Map<String, MaterialGroupData> codeToRow = new HashMap<>();

            for (MaterialGroupData row : materialGroups) {
                codeToRow.put(row.code, row);
                if (row.parentCode == null || row.parentCode.isEmpty()) {
                    rootNodes.add(row);
                } else {
                    childNodes.add(row);
                }
            }

            // 缓存已导入的物料组（code -> ID）
            Map<String, Long> codeToIdMap = new ConcurrentHashMap<>();

            // 第二遍：导入顶级节点（使用优化的事务管理）
            importRootMaterialGroupsWithTransaction(rootNodes, codeToIdMap, 
                    importedMaterialGroupCache, successCount, errors);

            // 第三遍：按层级导入子节点（使用优化的事务管理，批量处理）
            importChildMaterialGroupsBatchWithTransaction(childNodes, codeToRow, codeToIdMap, 
                    importedMaterialGroupCache, successCount, errors);

            // 返回结果
            int total = totalRows.get();
            int success = successCount.get();
            int failure = total - success;
            return new MaterialImportResponse.UnitGroupImportResult(total, success, failure, errors);
        }

        /**
         * 获取导入的物料组缓存，用于前缀匹配
         */
        public Map<String, MaterialGroup> getImportedMaterialGroupCache() {
            return importedMaterialGroupCache;
        }
    }

    /**
     * 导入顶级物料组（优化的事务管理，确保事务正确关闭）
     * 使用分批处理避免长时间占用连接，防止连接泄漏
     */
    private void importRootMaterialGroupsWithTransaction(List<MaterialGroupData> rootNodes,
                                                         Map<String, Long> codeToIdMap,
                                                         Map<String, MaterialGroup> importedMaterialGroupCache,
                                                         AtomicInteger successCount,
                                                         List<MaterialImportResponse.ImportError> errors) {
        if (rootNodes.isEmpty()) {
            return;
        }
        
        // 分批处理，每批处理一定数量的节点，避免单个事务时间过长导致连接泄漏
        // 每批使用独立事务，确保连接及时释放
        // 进一步减小批次大小到50，确保每批能在20秒内完成，避免超过60秒的泄漏检测阈值
        int batchSize = 50; // 每批处理50个顶级节点，确保快速完成
        
        // 在循环外创建事务模板，避免重复创建
        TransactionTemplate writeTemplate = new TransactionTemplate(transactionManager);
        writeTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        writeTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        writeTemplate.setTimeout(20); // 减少超时时间到20秒，强制更快完成
        
        for (int i = 0; i < rootNodes.size(); i += batchSize) {
            int end = Math.min(i + batchSize, rootNodes.size());
            List<MaterialGroupData> batch = rootNodes.subList(i, end);
            
            // 使用 execute 方法，确保事务正确提交或回滚
            try {
                writeTemplate.execute(status -> {
                    try {
                        int processedInBatch = 0;
                        for (MaterialGroupData row : batch) {
                            try {
                                // 直接使用 repository 的 insertOrGetByCode，避免嵌套事务
                                MaterialGroup materialGroup = materialGroupRepository.insertOrGetByCode(
                                        row.code, row.name != null ? row.name : row.code);
                                codeToIdMap.put(row.code, materialGroup.getId());
                                importedMaterialGroupCache.put(row.code, materialGroup);
                                successCount.incrementAndGet();
                                processedInBatch++;
                                
                                // 每处理25个节点刷新一次 EntityManager，更频繁地释放会话资源
                                if (processedInBatch % 25 == 0) {
                                    entityManager.flush();
                                }
                            } catch (Exception e) {
                                logger.debug("导入物料组第{}行失败: {}", row.rowNumber, e.getMessage());
                                if (errors.size() < MAX_ERROR_COUNT) {
                                    errors.add(new MaterialImportResponse.ImportError(
                                            "物料组", row.rowNumber, null, e.getMessage()));
                                }
                            }
                        }
                        // 批次结束前再次刷新，确保所有更改都写入数据库
                        entityManager.flush();
                    } catch (Exception e) {
                        logger.error("导入顶级物料组批次失败", e);
                        status.setRollbackOnly();
                        throw e;
                    }
                    return null;
                });
                // 事务已提交，连接已释放，可以安全地继续下一批
                logger.debug("顶级物料组批次 {}/{} 导入完成，已处理 {} 个节点", 
                        (i / batchSize) + 1, (rootNodes.size() + batchSize - 1) / batchSize, batch.size());
                
            } catch (org.springframework.transaction.TransactionException e) {
                // 捕获事务异常，可能是连接获取失败
                logger.error("导入顶级物料组批次事务失败（可能是连接池问题）: {}", e.getMessage(), e);
                // 等待一段时间后重试，给连接池时间恢复
                try {
                    Thread.sleep(100); // 等待100毫秒
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    logger.warn("批次处理被中断");
                    break;
                }
            } catch (Exception e) {
                logger.error("导入顶级物料组批次事务失败: {}", e.getMessage(), e);
                // 事务已经回滚，继续处理下一批
            }
            
            // 批次之间延迟，让连接池有时间回收连接
            // 每次延迟增加，避免连接池压力累积
            if (i + batchSize < rootNodes.size()) {
                try {
                    int delay = Math.min(50 + (i / batchSize) * 10, 200); // 延迟50-200毫秒
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    logger.warn("批次处理被中断");
                    break;
                }
            }
        }
    }

    /**
     * 批量导入子物料组（优化的事务管理，按层级批量处理）
     * 每个层级使用一个独立事务，确保连接正确释放
     */
    private void importChildMaterialGroupsBatchWithTransaction(List<MaterialGroupData> childNodes,
                                                              Map<String, MaterialGroupData> codeToRow,
                                                              Map<String, Long> codeToIdMap,
                                                              Map<String, MaterialGroup> importedMaterialGroupCache,
                                                              AtomicInteger successCount,
                                                              List<MaterialImportResponse.ImportError> errors) {
        if (childNodes.isEmpty()) {
            return;
        }
        
        List<MaterialGroupData> remainingNodes = new ArrayList<>(childNodes);
        int maxIterations = 100; // 防止无限循环，最多100层
        int iteration = 0;

        while (!remainingNodes.isEmpty() && iteration < maxIterations) {
            iteration++;
            List<MaterialGroupData> nextRoundNodes = new ArrayList<>();
            int totalProcessedCount = 0;
            
            // 分批处理当前层级的所有节点，避免单个事务处理过多节点导致连接泄漏
            // 进一步减小批次大小到50，确保每批能在20秒内完成，避免超过60秒的泄漏检测阈值
            int batchSize = 50; // 每批处理50个子节点，确保快速完成
            List<MaterialGroupData> nodesToProcessInThisRound = new ArrayList<>(remainingNodes);
            
            // 在循环外创建事务模板，避免重复创建
            TransactionTemplate batchTemplate = new TransactionTemplate(transactionManager);
            batchTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            batchTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
            batchTemplate.setTimeout(20); // 减少超时时间到20秒
            
            for (int i = 0; i < nodesToProcessInThisRound.size(); i += batchSize) {
                int end = Math.min(i + batchSize, nodesToProcessInThisRound.size());
                List<MaterialGroupData> batch = nodesToProcessInThisRound.subList(i, end);
                final int[] processedCount = {0};
                
                // 每个批次创建一个独立事务，事务结束后立即释放连接
                try {
                    List<MaterialGroupData> finalBatch = batch;
                    batchTemplate.execute(status -> {
                        try {
                            int processedInBatch = 0;
                            for (MaterialGroupData row : finalBatch) {
                                // 检查父节点是否已存在
                                Long parentId = codeToIdMap.get(row.parentCode);
                                if (parentId == null) {
                                    // 父节点还未导入，留到下一轮（在事务外处理）
                                    nextRoundNodes.add(row);
                                    continue;
                                }

                                try {
                                    // 直接使用 repository 的 insertOrGetByCodeWithParent，避免嵌套事务
                                    MaterialGroup materialGroup = materialGroupRepository.insertOrGetByCodeWithParent(
                                            row.code,
                                            row.name != null ? row.name : row.code,
                                            row.description,
                                            parentId);
                                    codeToIdMap.put(row.code, materialGroup.getId());
                                    importedMaterialGroupCache.put(row.code, materialGroup);
                                    processedCount[0]++;
                                    processedInBatch++;
                                    
                                    // 每处理25个节点刷新一次 EntityManager，更频繁地释放会话资源
                                    if (processedInBatch % 25 == 0) {
                                        entityManager.flush();
                                    }
                                } catch (Exception e) {
                                    logger.debug("导入物料组第{}行失败: {}", row.rowNumber, e.getMessage());
                                    if (errors.size() < MAX_ERROR_COUNT) {
                                        errors.add(new MaterialImportResponse.ImportError(
                                                "物料组", row.rowNumber, null, e.getMessage()));
                                    }
                                }
                            }
                            // 批次结束前再次刷新，确保所有更改都写入数据库
                            entityManager.flush();
                        } catch (Exception e) {
                            logger.error("批量导入物料组批次失败", e);
                            status.setRollbackOnly();
                            throw e;
                        }
                        return null;
                    });
                    // 事务已提交，连接已释放
                    totalProcessedCount += processedCount[0];
                } catch (org.springframework.transaction.TransactionException e) {
                    // 捕获事务异常，可能是连接获取失败
                    logger.warn("第{}轮物料组导入批次事务失败（可能是连接池问题）: {}", iteration, e.getMessage());
                    // 等待一段时间后继续，将批次中的节点标记为待处理
                    try {
                        Thread.sleep(100); // 等待100毫秒
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        logger.warn("批次处理被中断");
                        break;
                    }
                    nextRoundNodes.addAll(batch);
                } catch (Exception e) {
                    logger.warn("第{}轮物料组导入批次事务失败: {}", iteration, e.getMessage());
                    // 事务已回滚，将批次中的节点标记为待处理
                    nextRoundNodes.addAll(batch);
                }
                
                // 批次之间延迟，让连接池有时间回收连接
                if (i + batchSize < nodesToProcessInThisRound.size()) {
                    try {
                        int delay = Math.min(50 + (i / batchSize) * 10, 200); // 延迟50-200毫秒
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        logger.warn("批次处理被中断");
                        break;
                    }
                }
            }
            
            // 事务已提交，在事务外更新计数
            if (totalProcessedCount > 0) {
                successCount.addAndGet(totalProcessedCount);
                logger.debug("物料组导入第{}轮完成，处理了{}个节点，剩余{}个节点待处理", 
                        iteration, totalProcessedCount, nextRoundNodes.size());
            }

            // 如果本次有进展且还有剩余节点，继续处理下一轮
            if (totalProcessedCount > 0 && !nextRoundNodes.isEmpty()) {
                remainingNodes = nextRoundNodes;
            } else if (totalProcessedCount > 0 && nextRoundNodes.isEmpty()) {
                // 本次有进展且没有剩余节点，说明所有节点都已处理完成
                logger.debug("物料组导入完成，共{}轮，所有节点都已处理", iteration);
                break;
            } else if (!nextRoundNodes.isEmpty()) {
                // 本次没有进展但还有剩余节点，说明无法继续处理（可能是循环引用或缺失父节点）
                logger.warn("物料组导入无法继续，第{}轮没有进展，剩余{}个节点无法找到父节点", 
                        iteration, nextRoundNodes.size());
                for (MaterialGroupData row : nextRoundNodes) {
                    if (errors.size() < MAX_ERROR_COUNT) {
                        errors.add(new MaterialImportResponse.ImportError(
                                "物料组", row.rowNumber, "FParentId",
                                "父节点不存在: " + row.parentCode));
                    }
                }
                break;
            } else {
                // 没有剩余节点，所有节点都已处理
                break;
            }
        }

        if (iteration >= maxIterations) {
            logger.error("物料组导入达到最大迭代次数，可能存在循环引用");
            for (MaterialGroupData row : remainingNodes) {
                if (errors.size() < MAX_ERROR_COUNT) {
                    errors.add(new MaterialImportResponse.ImportError(
                            "物料组", row.rowNumber, null,
                            "导入失败：达到最大迭代次数，可能存在循环引用"));
                }
            }
        }
    }

    /**
     * 物料数据导入器
     */
    private class MaterialDataImporter implements ReadListener<MaterialExcelRow> {
        private final List<MaterialExcelRow> batch = new ArrayList<>();
        private final List<CompletableFuture<BatchResult>> futures = new ArrayList<>();
        private final AtomicInteger successCount = new AtomicInteger(0);
        private final AtomicInteger totalRows = new AtomicInteger(0);
        private final List<MaterialImportResponse.ImportError> errors = Collections.synchronizedList(new ArrayList<>());
        // 导入的物料组缓存，用于前缀匹配
        private final Map<String, MaterialGroup> importedMaterialGroupCache;
        
        public MaterialDataImporter(Map<String, MaterialGroup> importedMaterialGroupCache) {
            this.importedMaterialGroupCache = importedMaterialGroupCache != null 
                    ? importedMaterialGroupCache 
                    : new ConcurrentHashMap<>();
        }

        @Override
        public void invoke(MaterialExcelRow data, AnalysisContext context) {
            totalRows.incrementAndGet();

            String code = data.getCode();
            String name = data.getName();

            if (code == null || code.trim().isEmpty() || name == null || name.trim().isEmpty()) {
                int rowNum = context.readRowHolder().getRowIndex();
                if (errors.size() < MAX_ERROR_COUNT) {
                    errors.add(new MaterialImportResponse.ImportError(
                            "物料", rowNum, "FNumber", "物料编码或名称为空"));
                }
                return;
            }

            // 累积批次
            batch.add(data);
            if (batch.size() >= MATERIAL_BATCH_SIZE) {
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

        private void processBatchAsync(List<MaterialExcelRow> batchData) {
            // 异步提交批次处理任务到线程池，使用信号量限制并发
            CompletableFuture<BatchResult> future = CompletableFuture.supplyAsync(() -> {
                try {
                    // 获取信号量许可，限制并发批次数量
                    batchSemaphore.acquire();
                    try {
                        return processBatch(batchData);
                    } finally {
                        // 释放信号量许可
                        batchSemaphore.release();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.error("批次处理被中断", e);
                    throw new RuntimeException("批次处理被中断", e);
                }
            }, executorService);
            futures.add(future);
        }

        private BatchResult processBatch(List<MaterialExcelRow> batch) {
            // 检查线程是否被中断，避免在应用关闭时继续执行
            if (Thread.currentThread().isInterrupted()) {
                logger.warn("批次处理被中断，取消执行");
                return new BatchResult(0, new ArrayList<>());
            }
            
            // 在事务外进行预加载，减少连接占用时间
            // 先使用导入的物料组缓存
            Map<String, MaterialGroup> materialGroupCache = new HashMap<>(importedMaterialGroupCache);
            Map<String, Unit> unitCache = new HashMap<>();
            
            // 预加载数据（在事务外执行，但尽快完成）
            preloadAllData(batch, materialGroupCache, unitCache);
            
            // 预加载后再次检查中断
            if (Thread.currentThread().isInterrupted()) {
                logger.warn("预加载后被中断，取消执行");
                return new BatchResult(0, new ArrayList<>());
            }

            List<MaterialImportResponse.ImportError> batchErrors = new ArrayList<>();
            AtomicInteger batchSuccessCount = new AtomicInteger(0);

            // 每个批次在独立事务中处理，使用独立的事务模板确保连接正确释放
            // 减少事务超时时间到60秒，确保在连接泄漏检测阈值（120秒）之前完成
            TransactionTemplate writeTemplate = new TransactionTemplate(transactionManager);
            writeTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            writeTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
            writeTemplate.setTimeout(60); // 减少超时时间到60秒，确保在120秒泄漏检测阈值前完成
            
            try {
                writeTemplate.execute(status -> {
                    try {
                        int processedInBatch = 0;
                        for (MaterialExcelRow data : batch) {
                            // 在循环中检查中断，确保能够及时响应应用关闭
                            if (Thread.currentThread().isInterrupted()) {
                                logger.warn("事务执行中被中断，回滚事务");
                                status.setRollbackOnly();
                                throw new RuntimeException("事务被中断");
                            }
                            
                            try {
                                importMaterialRow(data, materialGroupCache, unitCache);
                                batchSuccessCount.incrementAndGet();
                                processedInBatch++;
                                
                                // 每处理200个物料刷新一次 EntityManager，释放会话资源
                                if (processedInBatch % 200 == 0) {
                                    entityManager.flush();
                                }
                            } catch (Exception e) {
                                // 批量处理中的错误降级为debug级别，减少日志输出
                                logger.debug("导入物料数据失败: {}", e.getMessage());
                                batchErrors.add(new MaterialImportResponse.ImportError(
                                        "物料", 0, null, e.getMessage()));
                            }
                        }
                        // 批次结束前再次刷新，确保所有更改都写入数据库
                        entityManager.flush();
                        return null;
                    } catch (Exception e) {
                        logger.error("批次事务执行失败", e);
                        status.setRollbackOnly();
                        throw e;
                    }
                });
            } catch (org.springframework.transaction.TransactionException e) {
                // 捕获事务异常，可能是连接获取失败或超时
                logger.error("批次事务异常（可能是连接池问题）: {}", e.getMessage(), e);
                throw e;
            }

            return new BatchResult(batchSuccessCount.get(), batchErrors);
        }

    /**
     * 预加载物料组和单位数据
     * 使用导入的物料组进行前缀匹配，不从数据库查询前缀匹配
     * 优化：使用批量查询替代循环查询，大幅提升性能
     */
    private void preloadAllData(List<MaterialExcelRow> batch, 
                                Map<String, MaterialGroup> materialGroupCache,
                                Map<String, Unit> unitCache) {
        Set<String> materialGroupCodes = new HashSet<>();
        Set<String> unitCodes = new HashSet<>();
        Set<String> materialCodesNeedingPrefix = new HashSet<>();
        
        // 收集所有需要查询的数据
        for (MaterialExcelRow row : batch) {
            String materialGroupCode = row.getMaterialGroupCode();
            String materialCode = row.getCode();
            String baseUnitCode = row.getBaseUnitCode();
            
            if (materialGroupCode != null && !materialGroupCode.isEmpty()) {
                materialGroupCodes.add(materialGroupCode);
            } else if (materialCode != null && !materialCode.isEmpty()) {
                materialCodesNeedingPrefix.add(materialCode);
            }
            
            if (baseUnitCode != null && !baseUnitCode.isEmpty()) {
                unitCodes.add(baseUnitCode);
            }
        }

        // 第一步：使用导入的物料组进行前缀匹配（在事务外，直接从内存缓存查找）
        // 如果导入的物料组中没有匹配到，就不再查询数据库
        if (!materialCodesNeedingPrefix.isEmpty()) {
            for (String materialCode : materialCodesNeedingPrefix) {
                MaterialGroup matchedGroup = findPrefixMatchFromImported(materialCode, materialGroupCache);
                if (matchedGroup != null) {
                    materialGroupCache.put("prefix:" + materialCode, matchedGroup);
                    logger.debug("从导入的物料组中前缀匹配成功：物料编码 {}, 匹配的物料组编码 {}", 
                            materialCode, matchedGroup.getCode());
                } else {
                    logger.debug("从导入的物料组中未找到前缀匹配：物料编码 {}", materialCode);
                }
            }
        }

        // 第二步：在单个只读事务中批量查询，减少数据库往返次数
        // 只查询物料组编码和单位，不进行前缀匹配查询
        if (!materialGroupCodes.isEmpty() || !unitCodes.isEmpty()) {
            // 检查中断
            if (Thread.currentThread().isInterrupted()) {
                logger.warn("预加载前被中断，跳过预加载");
                return;
            }
            
            TransactionTemplate readOnlyTemplate = new TransactionTemplate(transactionManager);
            readOnlyTemplate.setReadOnly(true);
            readOnlyTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            readOnlyTemplate.setTimeout(10); // 减少超时时间到10秒，确保快速完成并释放连接
            
            try {
                readOnlyTemplate.execute(status -> {
                    // 批量查询物料组：过滤掉已缓存的编码，使用IN查询一次性获取
                    List<String> materialGroupCodesToQuery = materialGroupCodes.stream()
                            .filter(code -> !materialGroupCache.containsKey(code))
                            .toList();
                    
                    if (!materialGroupCodesToQuery.isEmpty()) {
                        // 分批查询，避免IN查询参数过多
                        for (int i = 0; i < materialGroupCodesToQuery.size(); i += BATCH_QUERY_CHUNK_SIZE) {
                            // 检查中断
                            if (Thread.currentThread().isInterrupted()) {
                                logger.warn("预加载查询中被中断");
                                status.setRollbackOnly();
                                throw new RuntimeException("预加载被中断");
                            }
                            
                            int end = Math.min(i + BATCH_QUERY_CHUNK_SIZE, materialGroupCodesToQuery.size());
                            List<String> chunk = materialGroupCodesToQuery.subList(i, end);
                            materialGroupRepository.findByCodeIn(chunk).forEach(group -> {
                                materialGroupCache.put(group.getCode(), group);
                            });
                        }
                    }
                    
                    // 批量查询单位：使用IN查询一次性获取
                    if (!unitCodes.isEmpty()) {
                        List<String> unitCodesList = new ArrayList<>(unitCodes);
                        // 分批查询，避免IN查询参数过多
                        for (int i = 0; i < unitCodesList.size(); i += BATCH_QUERY_CHUNK_SIZE) {
                            // 检查中断
                            if (Thread.currentThread().isInterrupted()) {
                                logger.warn("单位预加载查询中被中断");
                                status.setRollbackOnly();
                                throw new RuntimeException("预加载被中断");
                            }
                            
                            int end = Math.min(i + BATCH_QUERY_CHUNK_SIZE, unitCodesList.size());
                            List<String> chunk = unitCodesList.subList(i, end);
                            unitRepository.findByCodeIn(chunk).forEach(unit -> {
                                unitCache.put(unit.getCode(), unit);
                            });
                        }
                    }
                    
                    return null;
                });
            } catch (Exception e) {
                // 预加载失败不应该阻止整个批次，记录日志但继续执行
                // 但如果是中断异常，应该重新抛出
                if (e instanceof RuntimeException && e.getMessage() != null && e.getMessage().contains("中断")) {
                    throw e;  // 重新抛出让外层处理中断
                }
                logger.warn("批次预加载失败，将跳过未预加载的数据: {}", e.getMessage());
            }
        }
        
        logger.debug("预加载完成：物料组 {} 个，单位 {} 个，前缀匹配 {} 个", 
                materialGroupCache.size(), unitCache.size(), materialCodesNeedingPrefix.size());
    }

    /**
     * 从导入的物料组中查找前缀匹配
     * 返回匹配的物料组，如果没有匹配则返回null
     */
    private MaterialGroup findPrefixMatchFromImported(String materialCode, Map<String, MaterialGroup> materialGroupCache) {
        MaterialGroup bestMatch = null;
        int maxLength = 0;
        
        // 遍历所有导入的物料组，查找最长的前缀匹配
        for (Map.Entry<String, MaterialGroup> entry : materialGroupCache.entrySet()) {
            String groupCode = entry.getKey();
            // 跳过特殊格式的key（prefix:xxx格式）
            if (groupCode.startsWith("prefix:")) {
                continue;
            }
            
            // 检查物料编码是否以物料组编码为前缀
            if (materialCode.startsWith(groupCode)) {
                // 选择最长匹配的前缀
                if (groupCode.length() > maxLength) {
                    maxLength = groupCode.length();
                    bestMatch = entry.getValue();
                }
            }
        }
        
        return bestMatch;
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
                logger.error("导入超时，取消所有未完成的任务", e);
                // 取消未完成的任务
                futures.forEach(f -> f.cancel(true));
                // 等待一段时间让虚拟线程响应中断并释放连接
                try {
                    Thread.sleep(1000);  // 等待1秒，让虚拟线程有时间响应中断
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    logger.warn("等待虚拟线程响应中断时被中断");
                }
                throw new RuntimeException("导入超时，请检查数据量或重试", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("导入被中断，取消所有未完成的任务", e);
                // 取消未完成的任务
                futures.forEach(f -> f.cancel(true));
                // 等待虚拟线程响应中断
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
                throw new RuntimeException("导入被中断", e);
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

        public MaterialImportResponse.MaterialImportResult getResult() {
            int total = totalRows.get();
            int success = successCount.get();
            int failure = total - success;
            return new MaterialImportResponse.MaterialImportResult(total, success, failure,
                    new ArrayList<>(errors));
        }
    }


    /**
     * 导入物料行数据
     */
    private void importMaterialRow(MaterialExcelRow data,
                                   Map<String, MaterialGroup> materialGroupCache,
                                   Map<String, Unit> unitCache) {
        String materialGroupCode = data.getMaterialGroupCode();
        String baseUnitCode = data.getBaseUnitCode();
        MaterialGroup materialGroup = null;

        // 如果物料组编码为空，尝试从缓存中获取前缀匹配的结果
        if (materialGroupCode == null || materialGroupCode.isEmpty()) {
            String materialCode = data.getCode();
            if (materialCode != null && !materialCode.isEmpty()) {
                // 从缓存中查找前缀匹配的结果（使用特殊key格式）
                materialGroup = materialGroupCache.get("prefix:" + materialCode);
                if (materialGroup != null) {
                    materialGroupCode = materialGroup.getCode();
                    logger.debug("根据物料编码前缀自动匹配物料组。物料编码: {}, 匹配的物料组编码: {}", 
                            materialCode, materialGroupCode);
                } else {
                    // 批量导入中的验证失败降级为debug级别，减少日志输出
                    logger.debug("物料组编码为空且无法根据前缀匹配，跳过导入。物料编码: {}, 物料名称: {}", 
                            materialCode, data.getName());
                    return;
                }
            } else {
                // 批量导入中的验证失败降级为debug级别，减少日志输出
                logger.debug("物料组编码为空且物料编码也为空，跳过导入。物料名称: {}", 
                        data.getName());
                return;
            }
        }
        
        if (baseUnitCode == null || baseUnitCode.isEmpty()) {
            // 批量导入中的验证失败降级为debug级别，减少日志输出
            logger.debug("基础单位编码为空，跳过导入。物料编码: {}, 物料名称: {}", 
                    data.getCode(), data.getName());
            return;
        }

        // 如果还没有获取到物料组，从缓存中查找
        if (materialGroup == null) {
            materialGroup = materialGroupCache.get(materialGroupCode);
            if (materialGroup == null) {
                throw new IllegalArgumentException("物料组不存在: " + materialGroupCode);
            }
        }

        Unit baseUnit = unitCache.get(baseUnitCode);
        if (baseUnit == null) {
            throw new IllegalArgumentException("基础单位不存在: " + baseUnitCode);
        }

        // 确保 MaterialGroup 和 Unit 都有 ID（已持久化）
        if (materialGroup.getId() == null) {
            throw new IllegalStateException("物料组未持久化，无法创建物料: " + data.getCode());
        }
        if (baseUnit.getId() == null) {
            throw new IllegalStateException("基础单位未持久化，无法创建物料: " + data.getCode());
        }

            // 直接使用 repository 的 insertOrGetByCode 方法，避免在事务内查询数据库
        // 使用缓存的 MaterialGroup 和 Unit 的 ID
        Material material = materialRepository.insertOrGetByCode(
                data.getCode(),
                data.getName() != null ? data.getName() : data.getCode(),
                materialGroup.getId(),
                baseUnit.getId()
        );

        // 设置关联对象（用于后续字段更新时使用，避免LazyInitializationException）
        // 注意：insertOrGetByCode返回的实体可能未加载关联对象，需要手动设置
        if (material.getMaterialGroup() == null) {
            material.setMaterialGroup(materialGroup);
        }
        if (material.getBaseUnit() == null) {
            material.setBaseUnit(baseUnit);
        }

        // 更新其他字段（如果有值）
        boolean needUpdate = false;
        if (data.getSpecification() != null && !data.getSpecification().trim().isEmpty()) {
            material.setSpecification(data.getSpecification().trim());
            needUpdate = true;
        }
        if (data.getMnemonicCode() != null && !data.getMnemonicCode().trim().isEmpty()) {
            material.setMnemonicCode(data.getMnemonicCode().trim());
            needUpdate = true;
        }
        if (data.getOldNumber() != null && !data.getOldNumber().trim().isEmpty()) {
            material.setOldNumber(data.getOldNumber().trim());
            needUpdate = true;
        }
        if (data.getDescription() != null && !data.getDescription().trim().isEmpty()) {
            material.setDescription(data.getDescription().trim());
            needUpdate = true;
        }

        // 只在需要更新字段时才保存，减少不必要的save调用
        // insertOrGetByCode已经返回持久化的实体，关联对象已在SQL中设置
        if (needUpdate) {
            materialRepository.save(material);
        }
    }

    /**
     * 物料组行数据
     */
    private static class MaterialGroupData {
        final int rowNumber;
        final String code;
        final String name;
        final String description;
        final String parentCode;

        MaterialGroupData(int rowNumber, String code, String name, String description, String parentCode) {
            this.rowNumber = rowNumber;
            this.code = code;
            this.name = name;
            this.description = description;
            this.parentCode = parentCode;
        }
    }

    /**
     * 批次处理结果
     */
    private record BatchResult(
            int successCount,
            List<MaterialImportResponse.ImportError> errors
    ) {}

}
