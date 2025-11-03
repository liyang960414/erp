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
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class MaterialImportService {

    private static final Logger logger = LoggerFactory.getLogger(MaterialImportService.class);

    private static final int MAX_ERROR_COUNT = 1000;
    // 批量查询时的分片大小，避免IN查询参数过多（PostgreSQL通常限制为32767）
    private static final int BATCH_QUERY_CHUNK_SIZE = 1000;

    @Getter
    private final MaterialGroupService materialGroupService;
    private final MaterialGroupRepository materialGroupRepository;
    private final MaterialRepository materialRepository;
    private final UnitRepository unitRepository;

    public MaterialImportService(
            MaterialGroupService materialGroupService,
            MaterialService materialService,
            MaterialGroupRepository materialGroupRepository,
            MaterialRepository materialRepository,
            UnitRepository unitRepository) {
        this.materialGroupService = materialGroupService;
        this.materialGroupRepository = materialGroupRepository;
        this.materialRepository = materialRepository;
        this.unitRepository = unitRepository;
    }

    public MaterialImportResponse importFromExcel(MultipartFile file) {
        logger.info("开始导入Excel文件: {}", file.getOriginalFilename());

        try {
            // 缓存文件内容
            byte[] fileBytes = file.getBytes();

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

            // 同步处理所有物料数据
            materialImporter.processAllMaterials();

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

        // 缓存已导入的物料组对象（code -> MaterialGroup）
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

        /**
         * 获取导入的物料组缓存
         */
        public Map<String, MaterialGroup> getImportedMaterialGroupCache() {
            return importedMaterialGroupCache;
        }
    }

    /**
     * 导入顶级物料组
     */
    private void importRootMaterialGroups(List<MaterialGroupData> rootNodes,
                                         Map<String, Long> codeToIdMap,
                                         Map<String, MaterialGroup> importedMaterialGroupCache,
                                         AtomicInteger successCount,
                                         List<MaterialImportResponse.ImportError> errors) {
        if (rootNodes.isEmpty()) {
            return;
        }

        for (MaterialGroupData row : rootNodes) {
            try {
                MaterialGroup materialGroup = materialGroupRepository.insertOrGetByCode(
                        row.code, row.name != null ? row.name : row.code);
                codeToIdMap.put(row.code, materialGroup.getId());
                importedMaterialGroupCache.put(row.code, materialGroup);
                successCount.incrementAndGet();
            } catch (Exception e) {
                logger.debug("导入物料组第{}行失败: {}", row.rowNumber, e.getMessage());
                if (errors.size() < MAX_ERROR_COUNT) {
                    errors.add(new MaterialImportResponse.ImportError(
                            "物料组", row.rowNumber, null, e.getMessage()));
                }
            }
        }
    }

    /**
     * 批量导入子物料组（按层级处理）
     */
    private void importChildMaterialGroups(List<MaterialGroupData> childNodes,
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
            int processedCount = 0;

            for (MaterialGroupData row : remainingNodes) {
                // 检查父节点是否已存在
                Long parentId = codeToIdMap.get(row.parentCode);
                if (parentId == null) {
                    // 父节点还未导入，留到下一轮
                    nextRoundNodes.add(row);
                    continue;
                }

                try {
                    MaterialGroup materialGroup = materialGroupRepository.insertOrGetByCodeWithParent(
                            row.code,
                            row.name != null ? row.name : row.code,
                            row.description,
                            parentId);
                    codeToIdMap.put(row.code, materialGroup.getId());
                    importedMaterialGroupCache.put(row.code, materialGroup);
                    processedCount++;
                } catch (Exception e) {
                    logger.debug("导入物料组第{}行失败: {}", row.rowNumber, e.getMessage());
                    if (errors.size() < MAX_ERROR_COUNT) {
                        errors.add(new MaterialImportResponse.ImportError(
                                "物料组", row.rowNumber, null, e.getMessage()));
                    }
                }
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
        private final List<MaterialExcelRow> allMaterials = new ArrayList<>();
        private final AtomicInteger successCount = new AtomicInteger(0);
        private final AtomicInteger totalRows = new AtomicInteger(0);
        private final List<MaterialImportResponse.ImportError> errors = new ArrayList<>();
        // 导入的物料组缓存
        private final Map<String, MaterialGroup> importedMaterialGroupCache;

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

            if (code == null || code.trim().isEmpty() || name == null || name.trim().isEmpty()) {
                int rowNum = context.readRowHolder().getRowIndex();
                if (errors.size() < MAX_ERROR_COUNT) {
                    errors.add(new MaterialImportResponse.ImportError(
                            "物料", rowNum, "FNumber", "物料编码或名称为空"));
                }
                return;
            }

            // 收集所有数据
            allMaterials.add(data);
        }

        @Override
        public void doAfterAllAnalysed(AnalysisContext context) {
            logger.info("物料数据收集完成，共 {} 条数据", allMaterials.size());
        }

        /**
         * 处理所有物料数据
         */
        public void processAllMaterials() {
            if (allMaterials.isEmpty()) {
                logger.info("没有需要处理的物料数据");
                return;
            }

            logger.info("开始处理 {} 条物料数据", allMaterials.size());

            // 预加载数据
            Map<String, MaterialGroup> materialGroupCache = new HashMap<>(importedMaterialGroupCache);
            Map<String, Unit> unitCache = new HashMap<>();
            preloadAllData(allMaterials, materialGroupCache, unitCache);

            // 逐个处理物料
            for (MaterialExcelRow data : allMaterials) {
                try {
                    importMaterialRow(data, materialGroupCache, unitCache);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    logger.debug("导入物料数据失败: {}", e.getMessage());
                    if (errors.size() < MAX_ERROR_COUNT) {
                        errors.add(new MaterialImportResponse.ImportError(
                                "物料", 0, null, e.getMessage()));
                    }
                }
            }
        }

        /**
         * 预加载物料组和单位数据
         * 优化：使用批量查询替代循环查询，大幅提升性能
         */
        private void preloadAllData(List<MaterialExcelRow> materials,
                                    Map<String, MaterialGroup> materialGroupCache,
                                    Map<String, Unit> unitCache) {
            Set<String> materialGroupCodes = new HashSet<>();
            Set<String> unitCodes = new HashSet<>();

            // 收集所有需要查询的数据
            for (MaterialExcelRow row : materials) {
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
            if (!unitCodes.isEmpty()) {
                List<String> unitCodesList = new ArrayList<>(unitCodes);
                // 分批查询，避免IN查询参数过多
                for (int i = 0; i < unitCodesList.size(); i += BATCH_QUERY_CHUNK_SIZE) {
                    int end = Math.min(i + BATCH_QUERY_CHUNK_SIZE, unitCodesList.size());
                    List<String> chunk = unitCodesList.subList(i, end);
                    unitRepository.findByCodeIn(chunk).forEach(unit -> {
                        unitCache.put(unit.getCode(), unit);
                    });
                }
            }

            logger.debug("预加载完成：物料组 {} 个，单位 {} 个",
                    materialGroupCache.size(), unitCache.size());
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

        // 如果物料组编码为空，直接跳过
        if (materialGroupCode == null || materialGroupCode.trim().isEmpty()) {
            logger.debug("物料组编码为空，跳过导入。物料编码: {}, 物料名称: {}",
                    data.getCode(), data.getName());
            return;
        }

        String baseUnitCode = data.getBaseUnitCode();
        if (baseUnitCode == null || baseUnitCode.trim().isEmpty()) {
            logger.debug("基础单位编码为空，跳过导入。物料编码: {}, 物料名称: {}",
                    data.getCode(), data.getName());
            return;
        }

        // 从缓存中查找物料组
        MaterialGroup materialGroup = materialGroupCache.get(materialGroupCode.trim());
        if (materialGroup == null) {
            throw new IllegalArgumentException("物料组不存在: " + materialGroupCode);
        }

        Unit baseUnit = unitCache.get(baseUnitCode.trim());
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

        // 直接使用 repository 的 insertOrGetByCode 方法
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
    private record MaterialGroupData(int rowNumber, String code, String name, String description, String parentCode) {
    }

}
