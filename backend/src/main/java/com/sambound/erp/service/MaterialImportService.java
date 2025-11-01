package com.sambound.erp.service;

import cn.idev.excel.FastExcel;
import cn.idev.excel.context.AnalysisContext;
import cn.idev.excel.metadata.data.ReadCellData;
import cn.idev.excel.read.listener.ReadListener;
import com.sambound.erp.dto.MaterialExcelRow;
import com.sambound.erp.dto.MaterialGroupExcelRow;
import com.sambound.erp.dto.MaterialImportResponse;
import com.sambound.erp.entity.MaterialGroup;
import com.sambound.erp.entity.Unit;
import com.sambound.erp.repository.MaterialGroupRepository;
import com.sambound.erp.repository.UnitRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
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

    // 批次大小
    private static final int MATERIAL_BATCH_SIZE = 500;
    private static final int MAX_ERROR_COUNT = 1000;

    private final MaterialGroupService materialGroupService;
    private final MaterialService materialService;
    private final MaterialGroupRepository materialGroupRepository;
    private final UnitRepository unitRepository;
    private final TransactionTemplate transactionTemplate;

    private final ExecutorService executorService;

    public MaterialImportService(
            MaterialGroupService materialGroupService,
            MaterialService materialService,
            MaterialGroupRepository materialGroupRepository,
            UnitRepository unitRepository,
            PlatformTransactionManager transactionManager) {
        this.materialGroupService = materialGroupService;
        this.materialService = materialService;
        this.materialGroupRepository = materialGroupRepository;
        this.unitRepository = unitRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.transactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        // 使用虚拟线程执行器（Java 25特性）
        this.executorService = Executors.newVirtualThreadPerTaskExecutor();
    }

    public MaterialImportResponse importFromExcel(MultipartFile file) {
        logger.info("开始导入Excel文件: {}", file.getOriginalFilename());

        try {
            // 缓存文件内容
            byte[] fileBytes = file.getBytes();

            // 处理物料组
            MaterialGroupImporter groupImporter = new MaterialGroupImporter();
            FastExcel.read(new ByteArrayInputStream(fileBytes), MaterialGroupExcelRow.class, groupImporter)
                    .sheet("数据分组#单据头(FBillHead)Group")
                    .headRowNumber(2)
                    .doRead();

            MaterialImportResponse.UnitGroupImportResult unitGroupResult = groupImporter.getResult();
            logger.info("物料组导入完成：总计 {} 条，成功 {} 条，失败 {} 条",
                    unitGroupResult.totalRows(), unitGroupResult.successCount(), unitGroupResult.failureCount());

            // 处理物料
            MaterialDataImporter materialImporter = new MaterialDataImporter();
            FastExcel.read(new ByteArrayInputStream(fileBytes), MaterialExcelRow.class, materialImporter)
                    .sheet("物料#物料(FBillHead)")
                    .headRowNumber(2)
                    .doRead();

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
     * 物料组导入器
     */
    private class MaterialGroupImporter implements ReadListener<MaterialGroupExcelRow> {
        private final List<MaterialGroupData> materialGroups = new ArrayList<>();
        private final AtomicInteger totalRows = new AtomicInteger(0);
        private final List<MaterialImportResponse.ImportError> errors = Collections.synchronizedList(new ArrayList<>());
        private final AtomicInteger successCount = new AtomicInteger(0);

        @Override
        public void invoke(MaterialGroupExcelRow data, AnalysisContext context) {
            totalRows.incrementAndGet();

            int rowNum = context.readRowHolder().getRowIndex();
            String code = data.getCode();
            String name = data.getName();

            if (code == null || code.trim().isEmpty() || name == null || name.trim().isEmpty()) {
                if (errors.size() < MAX_ERROR_COUNT) {
                    errors.add(new MaterialImportResponse.ImportError(
                            "物料组", rowNum, "FNumber", "物料组编码或名称为空"));
                }
                return;
            }

            materialGroups.add(new MaterialGroupData(rowNum, code.trim(), name.trim(),
                    data.getDescription() != null ? data.getDescription().trim() : null,
                    (data.getParentCode() != null && !data.getParentCode().trim().isEmpty())
                            ? data.getParentCode().trim() : null));
        }

        @Override
        public void doAfterAllAnalysed(AnalysisContext context) {
            if (materialGroups.isEmpty()) {
                logger.info("未找到物料组数据");
                return;
            }

            logger.info("找到 {} 条物料组数据，开始导入", materialGroups.size());

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

            // 第二遍：导入顶级节点
            transactionTemplate.execute(status -> {
                for (MaterialGroupData row : rootNodes) {
                    try {
                        MaterialGroup materialGroup = materialGroupService.findOrCreateByCode(
                                row.code, row.name, row.description, null);
                        codeToIdMap.put(row.code, materialGroup.getId());
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        logger.warn("导入物料组第{}行失败: {}", row.rowNumber, e.getMessage());
                        if (errors.size() < MAX_ERROR_COUNT) {
                            errors.add(new MaterialImportResponse.ImportError(
                                    "物料组", row.rowNumber, null, e.getMessage()));
                        }
                    }
                }
                return null;
            });

            // 第三遍：按层级导入子节点（递归处理）
            importChildMaterialGroups(childNodes, codeToRow, codeToIdMap, successCount);
        }

        private void importChildMaterialGroups(List<MaterialGroupData> childNodes,
                                              Map<String, MaterialGroupData> codeToRow,
                                              Map<String, Long> codeToIdMap,
                                              AtomicInteger successCount) {
            if (childNodes.isEmpty()) {
                return;
            }

            List<MaterialGroupData> remainingNodes = new ArrayList<>();
            final boolean[] hasProgress = {false};

            transactionTemplate.execute(status -> {
                for (MaterialGroupData row : childNodes) {
                    // 检查父节点是否已存在
                    Long parentId = codeToIdMap.get(row.parentCode);
                    if (parentId == null) {
                        // 父节点还未导入，留到下一轮
                        remainingNodes.add(row);
                        continue;
                    }

                    try {
                        MaterialGroup materialGroup = materialGroupService.findOrCreateByCode(
                                row.code, row.name, row.description, parentId);
                        codeToIdMap.put(row.code, materialGroup.getId());
                        successCount.incrementAndGet();
                        hasProgress[0] = true;
                    } catch (Exception e) {
                        logger.warn("导入物料组第{}行失败: {}", row.rowNumber, e.getMessage());
                        if (errors.size() < MAX_ERROR_COUNT) {
                            errors.add(new MaterialImportResponse.ImportError(
                                    "物料组", row.rowNumber, null, e.getMessage()));
                        }
                    }
                }
                return null;
            });

            // 如果还有未处理的节点且本次有进展，继续处理
            if (!remainingNodes.isEmpty() && hasProgress[0]) {
                importChildMaterialGroups(remainingNodes, codeToRow, codeToIdMap, successCount);
            } else if (!remainingNodes.isEmpty()) {
                // 无法继续处理，记录错误
                for (MaterialGroupData row : remainingNodes) {
                    if (errors.size() < MAX_ERROR_COUNT) {
                        errors.add(new MaterialImportResponse.ImportError(
                                "物料组", row.rowNumber, "FParentId",
                                "父节点不存在: " + row.parentCode));
                    }
                }
            }
        }

        public MaterialImportResponse.UnitGroupImportResult getResult() {
            int total = totalRows.get();
            int success = successCount.get();
            int failure = total - success;
            return new MaterialImportResponse.UnitGroupImportResult(total, success, failure,
                    new ArrayList<>(errors));
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

            // 等待所有批次处理完成
            waitForAllBatches();
        }

        private void processBatchAsync(List<MaterialExcelRow> batchData) {
            // 异步提交批次处理任务到线程池
            CompletableFuture<BatchResult> future = CompletableFuture.supplyAsync(() -> {
                return processBatch(batchData);
            }, executorService);
            futures.add(future);
        }

        private BatchResult processBatch(List<MaterialExcelRow> batch) {
            List<MaterialImportResponse.ImportError> batchErrors = new ArrayList<>();
            AtomicInteger batchSuccessCount = new AtomicInteger(0);

            // 预加载所有物料组和单位到缓存
            Map<String, MaterialGroup> materialGroupCache = new HashMap<>();
            Map<String, Unit> unitCache = new HashMap<>();

            Set<String> materialGroupCodes = new HashSet<>();
            Set<String> unitCodes = new HashSet<>();
            for (MaterialExcelRow row : batch) {
                if (row.getMaterialGroupCode() != null && !row.getMaterialGroupCode().isEmpty()) {
                    materialGroupCodes.add(row.getMaterialGroupCode());
                }
                if (row.getBaseUnitCode() != null && !row.getBaseUnitCode().isEmpty()) {
                    unitCodes.add(row.getBaseUnitCode());
                }
            }

            // 批量查询物料组和单位
            for (String code : materialGroupCodes) {
                materialGroupRepository.findByCode(code).ifPresent(group -> materialGroupCache.put(code, group));
            }
            for (String code : unitCodes) {
                unitRepository.findByCode(code).ifPresent(unit -> unitCache.put(code, unit));
            }

            logger.debug("预加载了 {} 个物料组和 {} 个单位", materialGroupCache.size(), unitCache.size());

            // 每个批次在独立事务中处理
            transactionTemplate.execute(status -> {
                for (MaterialExcelRow data : batch) {
                    try {
                        importMaterialRow(data, materialGroupCache, unitCache);
                        batchSuccessCount.incrementAndGet();
                    } catch (Exception e) {
                        logger.warn("导入物料数据失败: {}", e.getMessage());
                        batchErrors.add(new MaterialImportResponse.ImportError(
                                "物料", 0, null, e.getMessage()));
                    }
                }
                return null;
            });

            return new BatchResult(batchSuccessCount.get(), batchErrors);
        }

        private void waitForAllBatches() {
            try {
                // 等待所有批次完成（最多10分钟）
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                        .get(10, TimeUnit.MINUTES);

                // 收集所有批次的结果
                for (CompletableFuture<BatchResult> future : futures) {
                    try {
                        BatchResult result = future.get();
                        successCount.addAndGet(result.successCount());
                        errors.addAll(result.errors());
                    } catch (Exception e) {
                        logger.error("获取批次处理结果失败", e);
                    }
                }
            } catch (TimeoutException e) {
                logger.error("导入超时", e);
                throw new RuntimeException("导入超时，请检查数据量或重试", e);
            } catch (Exception e) {
                logger.error("批次处理失败", e);
                throw new RuntimeException("导入失败: " + e.getMessage(), e);
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

        if (materialGroupCode == null || materialGroupCode.isEmpty()) {
            logger.warn("物料组编码为空，跳过导入。物料编码: {}, 物料名称: {}", 
                    data.getCode(), data.getName());
            return;
        }
        if (baseUnitCode == null || baseUnitCode.isEmpty()) {
            logger.warn("基础单位编码为空，跳过导入。物料编码: {}, 物料名称: {}", 
                    data.getCode(), data.getName());
            return;
        }

        MaterialGroup materialGroup = materialGroupCache.get(materialGroupCode);
        if (materialGroup == null) {
            throw new IllegalArgumentException("物料组不存在: " + materialGroupCode);
        }

        Unit baseUnit = unitCache.get(baseUnitCode);
        if (baseUnit == null) {
            throw new IllegalArgumentException("基础单位不存在: " + baseUnitCode);
        }

        materialService.findOrCreateByCode(
                data.getCode(), data.getName(), data.getSpecification(),
                data.getMnemonicCode(), data.getOldNumber(), data.getDescription(),
                materialGroupCode, baseUnitCode);
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