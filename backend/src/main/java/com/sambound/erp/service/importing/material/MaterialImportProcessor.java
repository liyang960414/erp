package com.sambound.erp.service.importing.material;

import cn.idev.excel.FastExcel;
import cn.idev.excel.context.AnalysisContext;
import cn.idev.excel.read.listener.ReadListener;
import com.sambound.erp.config.ImportConfiguration;
import com.sambound.erp.service.importing.dto.MaterialExcelRow;
import com.sambound.erp.service.importing.dto.MaterialGroupExcelRow;
import com.sambound.erp.dto.MaterialImportResponse;
import com.sambound.erp.entity.Material;
import com.sambound.erp.entity.MaterialGroup;
import com.sambound.erp.entity.Unit;
import com.sambound.erp.service.importing.ImportError;
import com.sambound.erp.repository.MaterialGroupRepository;
import com.sambound.erp.repository.MaterialRepository;
import com.sambound.erp.repository.UnitRepository;
import lombok.Getter;
import com.sambound.erp.service.importing.exception.ImportProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayInputStream;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class MaterialImportProcessor {

    private static final Logger logger = LoggerFactory.getLogger(MaterialImportProcessor.class);

    private final MaterialGroupRepository materialGroupRepository;
    private final MaterialRepository materialRepository;
    private final UnitRepository unitRepository;
    private final TransactionTemplate transactionTemplate;
    private final ExecutorService executorService;
    private final ImportConfiguration importConfig;

    public MaterialImportProcessor(
            MaterialGroupRepository materialGroupRepository,
            MaterialRepository materialRepository,
            UnitRepository unitRepository,
            TransactionTemplate transactionTemplate,
            ExecutorService executorService,
            ImportConfiguration importConfig) {
        this.materialGroupRepository = materialGroupRepository;
        this.materialRepository = materialRepository;
        this.unitRepository = unitRepository;
        this.transactionTemplate = transactionTemplate;
        this.executorService = executorService;
        this.importConfig = importConfig;
    }

    public MaterialImportResponse process(byte[] fileBytes, String fileName) {
        logger.info("开始处理物料导入: {}", fileName);
        try {
            // 处理物料组：收集数据
            MaterialGroupCollector groupCollector = new MaterialGroupCollector();
            FastExcel.read(new ByteArrayInputStream(fileBytes), MaterialGroupExcelRow.class, groupCollector)
                    .sheet("数据分组#单据头(FBillHead)Group")
                    .headRowNumber(2)
                    .doRead();

            // 执行数据库导入操作
            MaterialImportResponse.UnitGroupImportResult unitGroupResult = groupCollector.importToDatabase();
            logger.info("物料组导入完成 [{}]: 总计 {} 条，成功 {} 条，失败 {} 条",
                    fileName, unitGroupResult.totalRows(), unitGroupResult.successCount(), unitGroupResult.failureCount());

            // 获取导入的物料组缓存
            Map<String, MaterialGroup> importedMaterialGroupCache = groupCollector.getImportedMaterialGroupCache();

            // 处理物料
            MaterialDataImporter materialImporter = new MaterialDataImporter(importedMaterialGroupCache);
            FastExcel.read(new ByteArrayInputStream(fileBytes), MaterialExcelRow.class, materialImporter)
                    .sheet("物料#物料(FBillHead)")
                    .headRowNumber(2)
                    .doRead();

            // 并行处理所有物料数据
            materialImporter.processAllMaterials();
            // 等待所有异步批次处理完成
            materialImporter.waitForCompletion();

            MaterialImportResponse.MaterialImportResult materialResult = materialImporter.getResult();
            logger.info("物料导入完成 [{}]: 总计 {} 条，成功 {} 条，失败 {} 条",
                    fileName, materialResult.totalRows(), materialResult.successCount(), materialResult.failureCount());

            return new MaterialImportResponse(unitGroupResult, materialResult);

        } catch (Exception e) {
            logger.error("物料导入处理失败", e);
            throw new ImportProcessingException("物料导入处理失败: " + e.getMessage(), e);
        }
    }

    /**
     * 物料组收集器
     */
    private class MaterialGroupCollector implements ReadListener<MaterialGroupExcelRow> {
        private final List<MaterialGroupData> allGroups = new ArrayList<>();
        private final AtomicInteger successCount = new AtomicInteger(0);
        private final AtomicInteger totalRows = new AtomicInteger(0);
        private final List<ImportError> errors = new ArrayList<>();
        @Getter
        private final Map<String, MaterialGroup> importedMaterialGroupCache = new HashMap<>();

        @Override
        public void invoke(MaterialGroupExcelRow data, AnalysisContext context) {
            totalRows.incrementAndGet();
            int rowNum = context.readRowHolder().getRowIndex();
            
            if (data.getCode() == null || data.getCode().trim().isEmpty()) {
                if (errors.size() < importConfig.getError().getMaxErrorCount()) {
                    errors.add(new ImportError("物料组", rowNum, "FNumber", "物料组编码为空"));
                }
                return;
            }
            
            allGroups.add(new MaterialGroupData(
                    rowNum,
                    data.getCode().trim(),
                    data.getName() != null ? data.getName().trim() : data.getCode().trim(),
                    data.getDescription(),
                    data.getParentCode() != null ? data.getParentCode().trim() : null
            ));
        }

        @Override
        public void doAfterAllAnalysed(AnalysisContext context) {
            logger.info("物料组数据收集完成，共 {} 条数据", allGroups.size());
        }

        public MaterialImportResponse.UnitGroupImportResult importToDatabase() {
            if (allGroups.isEmpty()) {
                return new MaterialImportResponse.UnitGroupImportResult(0, 0, 0, Collections.emptyList());
            }

            // 加载现有物料组到缓存
            List<String> allCodes = allGroups.stream().map(MaterialGroupData::code).toList();
            int chunkSize = importConfig.getBatch().getQueryChunkSize();
            for (int i = 0; i < allCodes.size(); i += chunkSize) {
                int end = Math.min(i + chunkSize, allCodes.size());
                List<String> chunk = allCodes.subList(i, end);
                materialGroupRepository.findByCodeIn(chunk).forEach(g -> 
                    importedMaterialGroupCache.put(g.getCode(), g));
            }

            // 拓扑排序/分层导入
            List<MaterialGroupData> remainingNodes = new ArrayList<>(allGroups);
            int maxIterations = 10; // 防止无限循环
            int iteration = 0;
            
            while (!remainingNodes.isEmpty() && iteration < maxIterations) {
                iteration++;
                List<MaterialGroupData> readyToImport = new ArrayList<>();
                List<MaterialGroupData> nextRoundNodes = new ArrayList<>();
                
                for (MaterialGroupData node : remainingNodes) {
                    // 如果已存在，跳过
                    if (importedMaterialGroupCache.containsKey(node.code())) {
                        continue;
                    }
                    
                    // 检查父节点是否就绪
                    boolean parentReady = node.parentCode() == null || 
                            node.parentCode().isEmpty() || 
                            importedMaterialGroupCache.containsKey(node.parentCode());
                            
                    if (parentReady) {
                        readyToImport.add(node);
                    } else {
                        nextRoundNodes.add(node);
                    }
                }
                
                if (readyToImport.isEmpty()) {
                    // 没有进展，可能是循环依赖或父节点缺失
                    for (MaterialGroupData node : nextRoundNodes) {
                        if (errors.size() < importConfig.getError().getMaxErrorCount()) {
                            errors.add(new ImportError("物料组", node.rowNumber(), "FParentId", 
                                    "父节点不存在或存在循环依赖: " + node.parentCode()));
                        }
                    }
                    break;
                }
                
                // 批量插入当前层级
                int processedCount = 0;
                int batchSize = importConfig.getBatch().getInsertSize();
                for (int i = 0; i < readyToImport.size(); i += batchSize) {
                    int end = Math.min(i + batchSize, readyToImport.size());
                    List<MaterialGroupData> batch = readyToImport.subList(i, end);
                    
                    try {
                        // 准备数据
                        List<MaterialGroup> entities = new ArrayList<>();
                        for (MaterialGroupData data : batch) {
                            MaterialGroup group = new MaterialGroup();
                            group.setCode(data.code());
                            group.setName(data.name());
                            group.setDescription(data.description());
                            if (data.parentCode() != null) {
                                group.setParent(importedMaterialGroupCache.get(data.parentCode()));
                            }
                            entities.add(group);
                        }
                        
                        // 事务内保存
                        List<MaterialGroup> saved = transactionTemplate.execute(status -> 
                            materialGroupRepository.saveAll(entities));
                            
                        // 更新缓存
                        if (saved != null) {
                            saved.forEach(g -> importedMaterialGroupCache.put(g.getCode(), g));
                            processedCount += saved.size();
                        }
                    } catch (Exception e) {
                        logger.error("物料组批量保存失败", e);
                        if (errors.size() < importConfig.getError().getMaxErrorCount()) {
                            errors.add(new ImportError("物料组", 0, null, "批量保存失败: " + e.getMessage()));
                        }
                    }
                }
                
                successCount.addAndGet(processedCount);
                remainingNodes = nextRoundNodes;
            }
            
            return new MaterialImportResponse.UnitGroupImportResult(
                    totalRows.get(), successCount.get(), totalRows.get() - successCount.get(), errors);
        }
    }

    /**
     * 物料数据导入器
     */
    private class MaterialDataImporter implements ReadListener<MaterialExcelRow> {
        private final List<MaterialRowData> allMaterials = new ArrayList<>();
        private final AtomicInteger successCount = new AtomicInteger(0);
        private final AtomicInteger totalRows = new AtomicInteger(0);
        private final List<ImportError> errors = Collections.synchronizedList(new ArrayList<>());
        // 导入的物料组缓存
        private final Map<String, MaterialGroup> importedMaterialGroupCache;
        // 异步批次任务列表
        private final List<CompletableFuture<BatchResult>> futures = new ArrayList<>();
        // 信号量：限制并发批次数量，避免连接池耗尽
        private final Semaphore batchSemaphore;

        public MaterialDataImporter(Map<String, MaterialGroup> importedMaterialGroupCache) {
            this.importedMaterialGroupCache = importedMaterialGroupCache != null
                    ? importedMaterialGroupCache
                    : new HashMap<>();
            this.batchSemaphore = new Semaphore(
                    MaterialImportProcessor.this.importConfig.getConcurrency().getMaxConcurrentBatches());
        }

        @Override
        public void invoke(MaterialExcelRow data, AnalysisContext context) {
            totalRows.incrementAndGet();

            String code = data.getCode();
            String name = data.getName();
            int rowNum = context.readRowHolder().getRowIndex();

            if (code == null || code.trim().isEmpty() || name == null || name.trim().isEmpty()) {
                if (errors.size() < MaterialImportProcessor.this.importConfig.getError().getMaxErrorCount()) {
                    String originalValue = String.format("Code: %s, Name: %s", code, name);
                    errors.add(new ImportError(
                            "物料", rowNum, "FNumber", "物料编码或名称为空", originalValue));
                }
                return;
            }

            // 收集所有数据（包含行号）
            allMaterials.add(new MaterialRowData(rowNum, data));
        }

        @Override
        public void doAfterAllAnalysed(AnalysisContext context) {
            logger.info("物料数据收集完成，共 {} 条数据", allMaterials.size());
        }

        /**
         * 批量处理所有物料数据（多线程并行）
         */
        public void processAllMaterials() {
            if (allMaterials.isEmpty()) {
                logger.info("没有需要处理的物料数据");
                return;
            }

            logger.info("开始并行批量处理 {} 条物料数据", allMaterials.size());

            // 预加载数据
            Map<String, MaterialGroup> materialGroupCache = new HashMap<>(importedMaterialGroupCache);
            Map<String, Unit> unitCache = new HashMap<>();
            Set<String> allMaterialGroupCodes = preloadAllData(allMaterials, materialGroupCache, unitCache);

            // 收集需要批量插入的物料数据（code -> batchData）
            Map<String, MaterialBatchData> codeToBatchData = new HashMap<>();
            
            for (MaterialRowData rowData : allMaterials) {
                MaterialExcelRow data = rowData.data();
                int rowNum = rowData.rowNumber();
                
                String materialGroupCode = data.getMaterialGroupCode();
                String baseUnitCode = data.getBaseUnitCode();
                
                // 如果物料组代码为空，尝试通过前缀匹配
                MaterialGroup materialGroup = null;
                if (materialGroupCode == null || materialGroupCode.trim().isEmpty()) {
                    // 尝试前缀匹配
                    materialGroup = MaterialImportProcessor.this.findMaterialGroupByPrefix(
                            data.getCode(), 
                            importedMaterialGroupCache, 
                            allMaterialGroupCodes);
                    
                    if (materialGroup == null) {
                        // 前缀匹配失败，记录错误并跳过
                        if (errors.size() < MaterialImportProcessor.this.importConfig.getError().getMaxErrorCount()) {
                            errors.add(new ImportError(
                                    "物料", rowNum, "FMaterialGroup",
                                    String.format("无法通过前缀匹配到物料组：%s", data.getCode()),
                                    data.getCode()));
                        }
                        continue;
                    }
                } else {
                    // 使用指定的物料组代码
                    materialGroup = materialGroupCache.get(materialGroupCode.trim());
                }
                
                // 验证必要字段
                if (baseUnitCode == null || baseUnitCode.trim().isEmpty()) {
                    if (errors.size() < MaterialImportProcessor.this.importConfig.getError().getMaxErrorCount()) {
                        errors.add(new ImportError(
                                "物料", rowNum, "FBaseUnitId", "基本单位编码为空", baseUnitCode));
                    }
                    continue;
                }
                
                Unit baseUnit = unitCache.get(baseUnitCode.trim());
                
                if (materialGroup == null || baseUnit == null) {
                    if (errors.size() < MaterialImportProcessor.this.importConfig.getError().getMaxErrorCount()) {
                        if (materialGroup == null) {
                            errors.add(new ImportError(
                                    "物料", rowNum, "FMaterialGroup",
                                    String.format("物料组不存在：%s", materialGroupCode),
                                    materialGroupCode));
                        }
                        if (baseUnit == null) {
                            errors.add(new ImportError(
                                    "物料", rowNum, "FBaseUnitId",
                                    String.format("基本单位不存在：%s", baseUnitCode),
                                    baseUnitCode));
                        }
                    }
                    continue;
                }
                
                if (materialGroup.getId() == null || baseUnit.getId() == null) {
                    continue;
                }
                
                codeToBatchData.put(data.getCode(), new MaterialBatchData(
                        data.getCode(),
                        data.getName() != null ? data.getName() : data.getCode(),
                        materialGroup.getId(),
                        baseUnit.getId(),
                        data.getErpClsId() != null && !data.getErpClsId().trim().isEmpty() 
                                ? data.getErpClsId().trim() : null,
                        data
                ));
            }

            // 分批并行插入物料
            List<MaterialBatchData> batchDataList = new ArrayList<>(codeToBatchData.values());
            int batchSize = MaterialImportProcessor.this.importConfig.getBatch().getInsertSize();
            
            for (int i = 0; i < batchDataList.size(); i += batchSize) {
                int end = Math.min(i + batchSize, batchDataList.size());
                List<MaterialBatchData> batch = new ArrayList<>(batchDataList.subList(i, end));
                int batchIndex = i / batchSize + 1;
                
                // 异步提交批次处理任务
                processBatchAsync(batch, batchIndex, codeToBatchData, materialGroupCache, unitCache);
            }
            
            // 注意：等待操作在外部调用 waitForCompletion() 方法执行
        }
        
        /**
         * 异步处理批次（批量插入）
         */
        private void processBatchAsync(List<MaterialBatchData> batch, int batchIndex,
                                      Map<String, MaterialBatchData> codeToBatchData,
                                      Map<String, MaterialGroup> materialGroupCache,
                                      Map<String, Unit> unitCache) {
            CompletableFuture<BatchResult> future = CompletableFuture.supplyAsync(() -> {
                try {
                    // 获取信号量许可，限制并发数量
                    batchSemaphore.acquire();
                    try {
                        return processBatch(batch, batchIndex, codeToBatchData, materialGroupCache, unitCache);
                    } finally {
                        // 释放信号量许可
                        batchSemaphore.release();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.warn("批次处理被中断: 批次{}", batchIndex);
                    return new BatchResult(0, List.of(new ImportError(
                            "物料", 0, null, "批次处理被中断")), new ArrayList<>());
                } catch (Exception e) {
                    logger.error("批次处理异常: 批次{}", batchIndex, e);
                    return new BatchResult(0, List.of(new ImportError(
                            "物料", 0, null, "批次处理异常: " + e.getMessage())), new ArrayList<>());
                }
            }, MaterialImportProcessor.this.executorService);
            futures.add(future);
        }
        
        /**
         * 处理单个批次（批量插入）
         */
        private BatchResult processBatch(List<MaterialBatchData> batch, int batchIndex,
                                        Map<String, MaterialBatchData> codeToBatchData,
                                        Map<String, MaterialGroup> materialGroupCache,
                                        Map<String, Unit> unitCache) {
            long startTime = System.currentTimeMillis();
            List<ImportError> batchErrors = new ArrayList<>();
            AtomicInteger batchSuccessCount = new AtomicInteger(0);
            List<Material> materialsToUpdate = new ArrayList<>();
            
            try {
                logger.debug("批次{}开始处理，共{}条数据", batchIndex, batch.size());
                // 在独立事务中批量插入
                // 准备插入数据（在事务外执行）
                List<MaterialRepository.MaterialBatchData> insertData = new ArrayList<>();
                for (MaterialBatchData data : batch) {
                    insertData.add(new MaterialRepository.MaterialBatchData(
                            data.code(),
                            data.name(),
                            data.materialGroupId(),
                            data.baseUnitId(),
                            data.erpClsId()
                    ));
                }

                // 在独立事务中批量插入
                List<Material> batchResult = MaterialImportProcessor.this.transactionTemplate.execute(status -> {
                    return materialRepository.batchInsertOrGetByCode(insertData);
                });
                
                // 建立编码到物料的映射（在事务外执行，避免持有连接）
                Map<String, Material> codeToMaterial = new HashMap<>();
                if (batchResult != null) {
                    for (Material material : batchResult) {
                        codeToMaterial.put(material.getCode(), material);
                    }
                }
                
                // 收集需要更新的物料（在事务外准备数据）
                for (MaterialBatchData data : batch) {
                    Material material = codeToMaterial.get(data.code());
                    if (material != null) {
                        // 设置关联对象
                        MaterialBatchData batchData = codeToBatchData.get(data.code());
                        MaterialExcelRow excelRow = batchData.excelRow();
                        
                        String materialGroupCode = excelRow.getMaterialGroupCode();
                        String baseUnitCode = excelRow.getBaseUnitCode();
                        
                        if (material.getMaterialGroup() == null && materialGroupCode != null && !materialGroupCode.trim().isEmpty()) {
                            material.setMaterialGroup(materialGroupCache.get(materialGroupCode.trim()));
                        }
                        if (material.getBaseUnit() == null && baseUnitCode != null && !baseUnitCode.trim().isEmpty()) {
                            material.setBaseUnit(unitCache.get(baseUnitCode.trim()));
                        }
                        
                        // 检查是否需要更新字段
                        boolean needUpdate = false;
                        
                        if (excelRow.getSpecification() != null && !excelRow.getSpecification().trim().isEmpty()) {
                            material.setSpecification(excelRow.getSpecification().trim());
                            needUpdate = true;
                        }
                        if (excelRow.getMnemonicCode() != null && !excelRow.getMnemonicCode().trim().isEmpty()) {
                            material.setMnemonicCode(excelRow.getMnemonicCode().trim());
                            needUpdate = true;
                        }
                        if (excelRow.getOldNumber() != null && !excelRow.getOldNumber().trim().isEmpty()) {
                            material.setOldNumber(excelRow.getOldNumber().trim());
                            needUpdate = true;
                        }
                        if (excelRow.getDescription() != null && !excelRow.getDescription().trim().isEmpty()) {
                            material.setDescription(excelRow.getDescription().trim());
                            needUpdate = true;
                        }
                        if (excelRow.getErpClsId() != null && !excelRow.getErpClsId().trim().isEmpty()) {
                            material.setErpClsId(excelRow.getErpClsId().trim());
                            needUpdate = true;
                        }
                        
                        if (needUpdate) {
                            materialsToUpdate.add(material);
                        }
                        
                        batchSuccessCount.incrementAndGet();
                    }
                }
                
                long insertTime = System.currentTimeMillis();
                long insertDuration = insertTime - startTime;
                
                logger.info("批次{}批量插入完成：处理{}条数据，耗时{}ms", 
                        batchIndex, batch.size(), insertDuration);
                
                // 批量更新需要更新的物料（在同一批次中处理）
                if (!materialsToUpdate.isEmpty()) {
                    long updateStartTime = System.currentTimeMillis();
                    MaterialImportProcessor.this.transactionTemplate.executeWithoutResult(status -> {
                        MaterialImportProcessor.this.materialRepository.saveAll(materialsToUpdate);
                    });
                    long updateDuration = System.currentTimeMillis() - updateStartTime;
                    logger.info("批次{}批量更新完成：更新{}条数据，耗时{}ms", 
                            batchIndex, materialsToUpdate.size(), updateDuration);
                }
                
                long totalDuration = System.currentTimeMillis() - startTime;
                logger.info("批次{}处理完成：成功{}条，总耗时{}ms，平均{}ms/条", 
                        batchIndex, batchSuccessCount.get(), totalDuration,
                        batchSuccessCount.get() > 0 ? totalDuration / batchSuccessCount.get() : 0);
                
            } catch (Exception e) {
                long failedDuration = System.currentTimeMillis() - startTime;
                logger.error("批次{}批量导入失败: {}，耗时{}ms", batchIndex, e.getMessage(), failedDuration, e);
                // 整批标记为失败
                if (batchErrors.size() < MaterialImportProcessor.this.importConfig.getError().getMaxErrorCount()) {
                    batchErrors.add(new ImportError(
                            "物料", 0, null,
                            String.format("批量导入失败（第%d批，%d条）: %s", batchIndex, batch.size(), e.getMessage())));
                }
            }
            
            return new BatchResult(batchSuccessCount.get(), batchErrors, materialsToUpdate);
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
                int timeoutMinutes = MaterialImportProcessor.this.importConfig.getTimeout().getProcessingTimeoutMinutes();
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                        .get(timeoutMinutes, TimeUnit.MINUTES);
                
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
        
        /**
         * 批次处理结果
         */
        private record BatchResult(
                int successCount,
                List<ImportError> errors,
                List<Material> materialsToUpdate
        ) {}
        
        /**
         * 物料批量插入数据（内部使用）
         */
        private record MaterialBatchData(
                String code,
                String name,
                Long materialGroupId,
                Long baseUnitId,
                String erpClsId,
                MaterialExcelRow excelRow
        ) {}
        
        /**
         * 物料行数据（包含行号）
         */
        private record MaterialRowData(
                int rowNumber,
                MaterialExcelRow data
        ) {}

        /**
         * 预加载物料组和单位数据
         * 优化：使用批量查询替代循环查询，大幅提升性能
         * 
         * @return 所有物料组代码集合（用于前缀匹配）
         */
        private Set<String> preloadAllData(List<MaterialRowData> materials,
                                    Map<String, MaterialGroup> materialGroupCache,
                                    Map<String, Unit> unitCache) {
            Set<String> materialGroupCodes = new HashSet<>();
            Set<String> unitCodes = new HashSet<>();

            // 收集所有需要查询的数据
            for (MaterialRowData rowData : materials) {
                MaterialExcelRow row = rowData.data();
                String materialGroupCode = row.getMaterialGroupCode();
                String baseUnitCode = row.getBaseUnitCode();

                if (materialGroupCode != null && !materialGroupCode.trim().isEmpty()) {
                    materialGroupCodes.add(materialGroupCode.trim());
                }

                if (baseUnitCode != null && !baseUnitCode.trim().isEmpty()) {
                    unitCodes.add(baseUnitCode.trim());
                }
            }

            // 批量查询物料组：过滤掉已缓存的编码，使用IN查询一次性获取
            List<String> materialGroupCodesToQuery = materialGroupCodes.stream()
                    .filter(code -> !materialGroupCache.containsKey(code))
                    .toList();

            if (!materialGroupCodesToQuery.isEmpty()) {
                // 分批查询，避免IN查询参数过多
                int chunkSize = MaterialImportProcessor.this.importConfig.getBatch().getQueryChunkSize();
                for (int i = 0; i < materialGroupCodesToQuery.size(); i += chunkSize) {
                    int end = Math.min(i + chunkSize, materialGroupCodesToQuery.size());
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
                int chunkSize = MaterialImportProcessor.this.importConfig.getBatch().getQueryChunkSize();
                for (int i = 0; i < unitCodesList.size(); i += chunkSize) {
                    int end = Math.min(i + chunkSize, unitCodesList.size());
                    List<String> chunk = unitCodesList.subList(i, end);
                    unitRepository.findByCodeIn(chunk).forEach(unit -> {
                        unitCache.put(unit.getCode(), unit);
                    });
                }
            }

            // 预加载所有物料组代码（用于前缀匹配）
            // 优化：只查询编码，避免加载完整实体
            Set<String> allMaterialGroupCodes = new HashSet<>();
            // 首先添加已缓存的物料组代码
            allMaterialGroupCodes.addAll(materialGroupCache.keySet());
            
            // 查询数据库中所有物料组编码（优化查询，只返回编码）
            List<String> allGroupCodes = MaterialImportProcessor.this.materialGroupRepository.findAllCodes();
            allMaterialGroupCodes.addAll(allGroupCodes);

            logger.debug("预加载完成：物料组 {} 个，单位 {} 个，所有物料组代码 {} 个",
                    materialGroupCache.size(), unitCache.size(), allMaterialGroupCodes.size());
            
            return allMaterialGroupCodes;
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
     * 物料组行数据
     */
    private record MaterialGroupData(int rowNumber, String code, String name, String description, String parentCode) {
    }

    /**
     * 通过物料编码前缀匹配物料组
     * 优先从导入缓存中匹配，再从数据库匹配
     * 使用最长匹配原则：如果有多个匹配，选择物料组代码最长的那个
     *
     * @param materialCode 物料编码
     * @param importedMaterialGroupCache 导入的物料组缓存（code -> MaterialGroup）
     * @param allMaterialGroupCodes 所有物料组代码集合（用于数据库匹配）
     * @return 匹配到的物料组，如果未找到返回 null
     */
    private MaterialGroup findMaterialGroupByPrefix(String materialCode,
                                                    Map<String, MaterialGroup> importedMaterialGroupCache,
                                                    Set<String> allMaterialGroupCodes) {
        if (materialCode == null || materialCode.trim().isEmpty()) {
            return null;
        }

        String trimmedCode = materialCode.trim();
        MaterialGroup matchedGroup = null;
        int maxLength = 0;

        // 1. 优先从导入缓存中匹配
        for (Map.Entry<String, MaterialGroup> entry : importedMaterialGroupCache.entrySet()) {
            String groupCode = entry.getKey();
            if (trimmedCode.startsWith(groupCode) && groupCode.length() > maxLength) {
                maxLength = groupCode.length();
                matchedGroup = entry.getValue();
            }
        }

        // 2. 如果缓存中未找到，从数据库的物料组代码集合中匹配
        if (matchedGroup == null && allMaterialGroupCodes != null) {
            String matchedCode = null;
            for (String groupCode : allMaterialGroupCodes) {
                if (trimmedCode.startsWith(groupCode) && groupCode.length() > maxLength) {
                    maxLength = groupCode.length();
                    matchedCode = groupCode;
                }
            }

            // 如果找到匹配的代码，从数据库查询对应的物料组
            if (matchedCode != null) {
                matchedGroup = materialGroupRepository.findByCode(matchedCode).orElse(null);
                if (matchedGroup != null) {
                    // 将查询到的物料组加入缓存，避免后续重复查询
                    importedMaterialGroupCache.put(matchedCode, matchedGroup);
                }
            }
        }

        return matchedGroup;
    }

}
