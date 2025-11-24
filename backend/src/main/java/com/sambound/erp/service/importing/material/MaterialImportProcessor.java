package com.sambound.erp.service.importing.material;

import cn.idev.excel.FastExcel;
import cn.idev.excel.context.AnalysisContext;
import cn.idev.excel.read.listener.ReadListener;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class MaterialImportProcessor {

    private static final Logger logger = LoggerFactory.getLogger(MaterialImportProcessor.class);

    private static final int MAX_ERROR_COUNT = 1000;
    // 批量查询时的分片大小，避免IN查询参数过多（PostgreSQL通常限制为32767）
    private static final int BATCH_QUERY_CHUNK_SIZE = 1000;
    // 批量插入时的分片大小，平衡性能和事务超时（每条记录约6个参数，1000条约6000个参数）
    // 减小批量大小以避免事务超时，同时保持合理性能
    private static final int BATCH_INSERT_SIZE = 1000;
    // 最大并发批次数量，限制为连接池大小的一半（留一些连接给其他操作）
    // 连接池通常为20，所以设置为10个并发批次
    private static final int MAX_CONCURRENT_BATCHES = 10;

    private final MaterialGroupRepository materialGroupRepository;
    private final MaterialRepository materialRepository;
    private final UnitRepository unitRepository;
    private final TransactionTemplate transactionTemplate;
    private final ExecutorService executorService;

    public MaterialImportProcessor(
            MaterialGroupRepository materialGroupRepository,
            MaterialRepository materialRepository,
            UnitRepository unitRepository,
            TransactionTemplate transactionTemplate,
            ExecutorService executorService) {
        this.materialGroupRepository = materialGroupRepository;
        this.materialRepository = materialRepository;
        this.unitRepository = unitRepository;
        this.transactionTemplate = transactionTemplate;
        this.executorService = executorService;
    }

    /**
     * 从输入流处理导入（新方法，支持流式读取）
     */
    public MaterialImportResponse process(InputStream inputStream) {
        try {
            // 由于需要读取两次（物料组和物料），需要将流转换为字节数组
            // 对于大文件，可以考虑使用支持 mark/reset 的 BufferedInputStream
            byte[] fileBytes = inputStream.readAllBytes();
            return process(fileBytes);
        } catch (Exception e) {
            logger.error("Excel文件导入失败", e);
            throw new RuntimeException("Excel文件导入失败: " + e.getMessage(), e);
        }
    }

    /**
     * 从字节数组处理导入（兼容旧代码）
     */
    public MaterialImportResponse process(byte[] fileBytes) {
        try {
            // 处理物料组：收集数据
            MaterialGroupCollector groupCollector = new MaterialGroupCollector();
            FastExcel.read(new ByteArrayInputStream(fileBytes), MaterialGroupExcelRow.class, groupCollector)
                    .sheet("数据分组#单据头(FBillHead)Group")
                    .headRowNumber(2)
                    .doRead();

            // 执行数据库导入操作
            MaterialImportResponse.UnitGroupImportResult unitGroupResult = groupCollector.importToDatabase();
            logger.info("物料组导入完成：总计 {} 条，成功 {} 条，失败 {} 条",
                    unitGroupResult.totalRows(), unitGroupResult.successCount(), unitGroupResult.failureCount());

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
            logger.info("物料导入完成：总计 {} 条，成功 {} 条，失败 {} 条",
                    materialResult.totalRows(), materialResult.successCount(), materialResult.failureCount());

            return new MaterialImportResponse(unitGroupResult, materialResult);
        } catch (Exception e) {
            logger.error("Excel文件导入失败", e);
            throw new RuntimeException("Excel文件导入失败: " + e.getMessage(), e);
        }
    }

    /**
     * 物料组收集器：只负责收集数据
     */
    private class MaterialGroupCollector implements ReadListener<MaterialGroupExcelRow> {
        private final List<MaterialGroupData> materialGroups = new ArrayList<>();
        private final AtomicInteger totalRows = new AtomicInteger(0);

        /**
         * -- GETTER --
         *  获取导入的物料组缓存
         */
        // 缓存已导入的物料组对象（code -> MaterialGroup）
        @Getter
        private final Map<String, MaterialGroup> importedMaterialGroupCache = new HashMap<>();

        @Override
        public void invoke(MaterialGroupExcelRow data, AnalysisContext context) {
            totalRows.incrementAndGet();

            int rowNum = context.readRowHolder().getRowIndex();
            String code = data.getCode();
            String name = data.getName();

            if (code == null || code.trim().isEmpty() || name == null || name.trim().isEmpty()) {
                return;
            }

            materialGroups.add(new MaterialGroupData(rowNum, code.trim(), name.trim(),
                    data.getDescription() != null ? data.getDescription().trim() : null,
                    (data.getParentCode() != null && !data.getParentCode().trim().isEmpty())
                            ? data.getParentCode().trim() : null));
        }

        @Override
        public void doAfterAllAnalysed(AnalysisContext context) {
            logger.info("物料组数据收集完成，共 {} 条数据", materialGroups.size());
        }

        /**
         * 执行数据库导入操作
         */
        public MaterialImportResponse.UnitGroupImportResult importToDatabase() {
            if (materialGroups.isEmpty()) {
                logger.info("未找到物料组数据");
                return new MaterialImportResponse.UnitGroupImportResult(
                        0, 0, 0, new ArrayList<>());
            }

            logger.info("找到 {} 条物料组数据，开始导入到数据库", materialGroups.size());

            List<ImportError> errors = new ArrayList<>();
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
            Map<String, Long> codeToIdMap = new HashMap<>();

            // 第二遍：导入顶级节点
            importRootMaterialGroups(rootNodes, codeToIdMap, importedMaterialGroupCache, successCount, errors);

            // 第三遍：按层级导入子节点
            importChildMaterialGroups(childNodes, codeToRow, codeToIdMap, importedMaterialGroupCache, successCount, errors);

            // 返回结果
            int total = totalRows.get();
            int success = successCount.get();
            int failure = total - success;
            return new MaterialImportResponse.UnitGroupImportResult(total, success, failure, errors);
        }

    }

    /**
     * 批量导入顶级物料组（单次事务，数据量小）
     * 优化：将非数据库操作移到事务外，缩短事务持有连接的时间
     */
    private void importRootMaterialGroups(List<MaterialGroupData> rootNodes,
                                         Map<String, Long> codeToIdMap,
                                         Map<String, MaterialGroup> importedMaterialGroupCache,
                                         AtomicInteger successCount,
                                         List<ImportError> errors) {
        if (rootNodes.isEmpty()) {
            return;
        }

        long startTime = System.currentTimeMillis();
        
        // 在事务外准备数据
        List<String> codes = new ArrayList<>();
        List<String> names = new ArrayList<>();
        
        for (MaterialGroupData row : rootNodes) {
            codes.add(row.code);
            names.add(row.name != null ? row.name : row.code);
        }
        
        // 在独立事务中仅执行数据库操作
        Map<String, MaterialGroup> batchResult;
        try {
            batchResult = transactionTemplate.execute(status -> {
                // 仅执行数据库批量插入操作，不进行任何其他操作
                return materialGroupRepository.batchInsertOrGetByCode(codes, names);
            });
        } catch (Exception e) {
            long failedDuration = System.currentTimeMillis() - startTime;
            logger.error("批量导入顶级物料组失败: {}，耗时{}ms", e.getMessage(), failedDuration, e);
            // 整批标记为失败
            int startRow = rootNodes.get(0).rowNumber;
            int endRow = rootNodes.get(rootNodes.size() - 1).rowNumber;
            if (errors.size() < MAX_ERROR_COUNT) {
                errors.add(new ImportError(
                        "物料组", startRow, null, 
                        String.format("批量导入失败（行%d-%d）: %s", startRow, endRow, e.getMessage())));
            }
            return;
        }
        
        // 在事务外更新缓存和计数（不持有数据库连接）
        int actualSuccessCount = 0;
        if (batchResult != null) {
            for (MaterialGroupData row : rootNodes) {
                MaterialGroup materialGroup = batchResult.get(row.code);
                if (materialGroup != null) {
                    codeToIdMap.put(row.code, materialGroup.getId());
                    importedMaterialGroupCache.put(row.code, materialGroup);
                    actualSuccessCount++;
                }
            }
        }
        
        long duration = System.currentTimeMillis() - startTime;
        successCount.addAndGet(actualSuccessCount);
        logger.info("批量导入顶级物料组完成：处理{}个节点，成功{}个，耗时{}ms，平均{}ms/个", 
                rootNodes.size(), actualSuccessCount, duration,
                rootNodes.size() > 0 ? duration / rootNodes.size() : 0);
    }

    /**
     * 批量导入子物料组（按层级处理）
     */
    private void importChildMaterialGroups(List<MaterialGroupData> childNodes,
                                          Map<String, MaterialGroupData> codeToRow,
                                          Map<String, Long> codeToIdMap,
                                          Map<String, MaterialGroup> importedMaterialGroupCache,
                                          AtomicInteger successCount,
                                          List<ImportError> errors) {
        if (childNodes.isEmpty()) {
            return;
        }

        List<MaterialGroupData> remainingNodes = new ArrayList<>(childNodes);
        int maxIterations = 100; // 防止无限循环，最多100层
        int iteration = 0;

        while (!remainingNodes.isEmpty() && iteration < maxIterations) {
            iteration++;
            List<MaterialGroupData> nextRoundNodes = new ArrayList<>();
            List<MaterialGroupData> readyToImport = new ArrayList<>();
            
            // 第一遍：分离出可以导入的节点
            for (MaterialGroupData row : remainingNodes) {
                Long parentId = codeToIdMap.get(row.parentCode);
                if (parentId == null) {
                    // 父节点还未导入，留到下一轮
                    nextRoundNodes.add(row);
                } else {
                    // 父节点已存在，可以导入
                    readyToImport.add(row);
                }
            }
            
            // 第二遍：批量导入可以导入的节点（单次事务，数据量小）
            int processedCount = 0;
            if (!readyToImport.isEmpty()) {
                long startTime = System.currentTimeMillis();
                
                // 在事务外准备数据
                List<MaterialGroupRepository.MaterialGroupBatchData> batchData = new ArrayList<>();
                for (MaterialGroupData row : readyToImport) {
                    Long parentId = codeToIdMap.get(row.parentCode);
                    batchData.add(new MaterialGroupRepository.MaterialGroupBatchData(
                            row.code,
                            row.name != null ? row.name : row.code,
                            row.description,
                            parentId));
                }
                
                // 在独立事务中仅执行数据库操作
                Map<String, MaterialGroup> batchResult;
                try {
                    batchResult = transactionTemplate.execute(status -> {
                        // 仅执行数据库批量插入操作，不进行任何其他操作
                        return materialGroupRepository.batchInsertOrGetByCodeWithParent(batchData);
                    });
                } catch (Exception e) {
                    long failedDuration = System.currentTimeMillis() - startTime;
                    logger.error("物料组导入第{}轮失败: {}，耗时{}ms", iteration, e.getMessage(), failedDuration, e);
                    // 整批标记为失败
                    int startRow = readyToImport.get(0).rowNumber;
                    int endRow = readyToImport.get(readyToImport.size() - 1).rowNumber;
                    if (errors.size() < MAX_ERROR_COUNT) {
                        errors.add(new ImportError(
                                "物料组", startRow, null,
                                String.format("批量导入失败（行%d-%d）: %s", startRow, endRow, e.getMessage())));
                    }
                    // 失败的不计入processedCount，避免计数错误
                    continue;
                }
                
                // 在事务外更新缓存和计数（不持有数据库连接）
                if (batchResult != null) {
                    for (MaterialGroupData row : readyToImport) {
                        MaterialGroup materialGroup = batchResult.get(row.code);
                        if (materialGroup != null) {
                            codeToIdMap.put(row.code, materialGroup.getId());
                            importedMaterialGroupCache.put(row.code, materialGroup);
                            processedCount++;
                        }
                    }
                }
                
                long duration = System.currentTimeMillis() - startTime;
                logger.info("物料组导入第{}轮完成：处理{}个节点，成功{}个，耗时{}ms，平均{}ms/个",
                        iteration, readyToImport.size(), processedCount, duration,
                        readyToImport.size() > 0 ? duration / readyToImport.size() : 0);
            }

            if (processedCount > 0) {
                successCount.addAndGet(processedCount);
                logger.debug("物料组导入第{}轮完成，处理了{}个节点，剩余{}个节点待处理",
                        iteration, processedCount, nextRoundNodes.size());
            }

            // 如果本次有进展且还有剩余节点，继续处理下一轮
            if (processedCount > 0 && !nextRoundNodes.isEmpty()) {
                remainingNodes = nextRoundNodes;
            } else if (processedCount > 0) {
                // 本次有进展且没有剩余节点，说明所有节点都已处理完成
                logger.debug("物料组导入完成，共{}轮，所有节点都已处理", iteration);
                break;
            } else if (!nextRoundNodes.isEmpty()) {
                // 本次没有进展但还有剩余节点，说明无法继续处理（可能是循环引用或缺失父节点）
                logger.warn("物料组导入无法继续，第{}轮没有进展，剩余{}个节点无法找到父节点",
                        iteration, nextRoundNodes.size());
                for (MaterialGroupData row : nextRoundNodes) {
                    if (errors.size() < MAX_ERROR_COUNT) {
                        errors.add(new ImportError(
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
                    errors.add(new ImportError(
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
        private final List<MaterialRowData> allMaterials = new ArrayList<>();
        private final AtomicInteger successCount = new AtomicInteger(0);
        private final AtomicInteger totalRows = new AtomicInteger(0);
        private final List<ImportError> errors = Collections.synchronizedList(new ArrayList<>());
        // 导入的物料组缓存
        private final Map<String, MaterialGroup> importedMaterialGroupCache;
        // 异步批次任务列表
        private final List<CompletableFuture<BatchResult>> futures = new ArrayList<>();
        // 信号量：限制并发批次数量，避免连接池耗尽
        private final Semaphore batchSemaphore = new Semaphore(MAX_CONCURRENT_BATCHES);

        public MaterialDataImporter(Map<String, MaterialGroup> importedMaterialGroupCache) {
            this.importedMaterialGroupCache = importedMaterialGroupCache != null
                    ? importedMaterialGroupCache
                    : new HashMap<>();
        }

        @Override
        public void invoke(MaterialExcelRow data, AnalysisContext context) {
            totalRows.incrementAndGet();

            String code = data.getCode();
            String name = data.getName();
            int rowNum = context.readRowHolder().getRowIndex();

            if (code == null || code.trim().isEmpty() || name == null || name.trim().isEmpty()) {
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
                        if (errors.size() < MAX_ERROR_COUNT) {
                            errors.add(new ImportError(
                                    "物料", rowNum, "FMaterialGroup",
                                    String.format("无法通过前缀匹配到物料组：%s", data.getCode())));
                        }
                        continue;
                    }
                } else {
                    // 使用指定的物料组代码
                    materialGroup = materialGroupCache.get(materialGroupCode.trim());
                }
                
                // 验证必要字段
                if (baseUnitCode == null || baseUnitCode.trim().isEmpty()) {
                    if (errors.size() < MAX_ERROR_COUNT) {
                        errors.add(new ImportError(
                                "物料", rowNum, "FBaseUnitId", "基本单位编码为空"));
                    }
                    continue;
                }
                
                Unit baseUnit = unitCache.get(baseUnitCode.trim());
                
                if (materialGroup == null || baseUnit == null) {
                    if (errors.size() < MAX_ERROR_COUNT) {
                        if (materialGroup == null) {
                            errors.add(new ImportError(
                                    "物料", rowNum, "FMaterialGroup",
                                    String.format("物料组不存在：%s", materialGroupCode)));
                        }
                        if (baseUnit == null) {
                            errors.add(new ImportError(
                                    "物料", rowNum, "FBaseUnitId",
                                    String.format("基本单位不存在：%s", baseUnitCode)));
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
            
            for (int i = 0; i < batchDataList.size(); i += BATCH_INSERT_SIZE) {
                int end = Math.min(i + BATCH_INSERT_SIZE, batchDataList.size());
                List<MaterialBatchData> batch = new ArrayList<>(batchDataList.subList(i, end));
                int batchIndex = i / BATCH_INSERT_SIZE + 1;
                
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
                List<Material> batchResult = MaterialImportProcessor.this.transactionTemplate.execute(status -> {
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
                if (batchErrors.size() < MAX_ERROR_COUNT) {
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
                // 等待所有批次完成（最多30分钟）
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                        .get(30, TimeUnit.MINUTES);
                
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
                for (int i = 0; i < materialGroupCodesToQuery.size(); i += BATCH_QUERY_CHUNK_SIZE) {
                    int end = Math.min(i + BATCH_QUERY_CHUNK_SIZE, materialGroupCodesToQuery.size());
                    List<String> chunk = materialGroupCodesToQuery.subList(i, end);
                    materialGroupRepository.findByCodeIn(chunk).forEach(group -> {
                        materialGroupCache.put(group.getCode(), group);
                    });
                }
            }

            // 批量查询单位：使用IN查询一次性获取
            // 使用 JOIN FETCH 预加载 UnitGroup，避免 LazyInitializationException
            if (!unitCodes.isEmpty()) {
                List<String> unitCodesList = new ArrayList<>(unitCodes);
                // 分批查询，避免IN查询参数过多
                for (int i = 0; i < unitCodesList.size(); i += BATCH_QUERY_CHUNK_SIZE) {
                    int end = Math.min(i + BATCH_QUERY_CHUNK_SIZE, unitCodesList.size());
                    List<String> chunk = unitCodesList.subList(i, end);
                    List<Unit> units = unitRepository.findByCodeInWithUnitGroup(chunk);
                    for (Unit unit : units) {
                        // 确保 UnitGroup 完全初始化（在事务内）
                        // 访问多个字段以确保代理对象被完全初始化
                        if (unit.getUnitGroup() != null) {
                            unit.getUnitGroup().getId();
                            unit.getUnitGroup().getCode();
                            unit.getUnitGroup().getName();
                        }
                        unitCache.put(unit.getCode(), unit);
                    }
                }
            }

            // 预加载所有物料组代码（用于前缀匹配）
            // 查询数据库中所有物料组代码，过滤掉已经缓存的
            Set<String> allMaterialGroupCodes = new HashSet<>();
            // 首先添加已缓存的物料组代码
            allMaterialGroupCodes.addAll(materialGroupCache.keySet());
            
            // 查询数据库中所有物料组代码（分批查询以避免一次性加载过多）
            List<MaterialGroup> allGroups = MaterialImportProcessor.this.materialGroupRepository.findAll();
            for (MaterialGroup group : allGroups) {
                String code = group.getCode();
                allMaterialGroupCodes.add(code);
                // 如果不在缓存中，也加入缓存
                if (!materialGroupCache.containsKey(code)) {
                    materialGroupCache.put(code, group);
                }
            }

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
