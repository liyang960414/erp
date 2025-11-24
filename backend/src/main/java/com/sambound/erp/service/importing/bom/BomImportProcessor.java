package com.sambound.erp.service.importing.bom;

import cn.idev.excel.FastExcel;
import cn.idev.excel.context.AnalysisContext;
import cn.idev.excel.read.listener.ReadListener;
import com.sambound.erp.dto.BomImportResponse;
import com.sambound.erp.entity.BillOfMaterial;
import com.sambound.erp.entity.BomItem;
import com.sambound.erp.entity.Material;
import com.sambound.erp.entity.Unit;
import com.sambound.erp.repository.BillOfMaterialRepository;
import com.sambound.erp.repository.BomItemRepository;
import com.sambound.erp.repository.MaterialRepository;
import com.sambound.erp.repository.UnitRepository;
import com.sambound.erp.service.importing.dto.BomExcelRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * BOM 导入处理器，负责解析 Excel 并将数据写入数据库。
 */
public class BomImportProcessor {

    private static final Logger logger = LoggerFactory.getLogger(BomImportProcessor.class);

    private static final int MAX_ERROR_COUNT = 1000;
    private static final int BATCH_QUERY_CHUNK_SIZE = 1000;
    private static final int BOM_BATCH_SIZE = 500;

    private final BillOfMaterialRepository bomRepository;
    private final BomItemRepository bomItemRepository;
    private final MaterialRepository materialRepository;
    private final UnitRepository unitRepository;
    private final TransactionTemplate transactionTemplate;

    public BomImportProcessor(
            BillOfMaterialRepository bomRepository,
            BomItemRepository bomItemRepository,
            MaterialRepository materialRepository,
            UnitRepository unitRepository,
            TransactionTemplate transactionTemplate) {
        this.bomRepository = bomRepository;
        this.bomItemRepository = bomItemRepository;
        this.materialRepository = materialRepository;
        this.unitRepository = unitRepository;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * 从输入流处理导入（新方法，支持流式读取）
     */
    public BomImportResponse process(InputStream inputStream) {
        try {
            BomDataCollector collector = new BomDataCollector();
            FastExcel.read(inputStream, BomExcelRow.class, collector)
                    .sheet("物料清单#单据头(FBillHead)")
                    .headRowNumber(2)
                    .doRead();
            return collector.importToDatabase();
        } catch (Exception e) {
            logger.error("BOM Excel 导入失败", e);
            throw new RuntimeException("BOM Excel 导入失败: " + e.getMessage(), e);
        }
    }

    private class BomDataCollector implements ReadListener<BomExcelRow> {

        private final List<BomData> bomDataList = new ArrayList<>();
        private final AtomicInteger totalRows = new AtomicInteger(0);
        private BomHeader currentHeader = null;

        @Override
        public void invoke(BomExcelRow data, AnalysisContext context) {
            totalRows.incrementAndGet();
            int rowNum = context.readRowHolder().getRowIndex();

            if ((data.getBillHead() != null && !data.getBillHead().trim().isEmpty())
                    || (data.getMaterialCode() != null && !data.getMaterialCode().trim().isEmpty())) {
                String version = null;
                if (data.getVersion() != null && !data.getVersion().trim().isEmpty()) {
                    version = data.getVersion().trim();
                }
                currentHeader = new BomHeader(
                        rowNum,
                        trimToNull(data.getMaterialCode()),
                        version,
                        data.getName(),
                        data.getCategory(),
                        data.getUsage(),
                        data.getDescription()
                );
            }

            if (currentHeader != null
                    && data.getChildMaterialCode() != null
                    && !data.getChildMaterialCode().trim().isEmpty()) {
                BomItemData itemData = new BomItemData(
                        rowNum,
                        parseInteger(data.getSequence()),
                        data.getChildMaterialCode().trim(),
                        trimToNull(data.getChildUnitCode()),
                        trimToNull(data.getNumerator()),
                        trimToNull(data.getDenominator()),
                        trimToNull(data.getScrapRate()),
                        trimToNull(data.getChildBomVersion()),
                        data.getMemo()
                );
                bomDataList.add(new BomData(currentHeader, itemData));
            }
        }

        @Override
        public void doAfterAllAnalysed(AnalysisContext context) {
            logger.info("BOM 数据收集完成，共 {} 条明细记录", bomDataList.size());
        }

        public BomImportResponse importToDatabase() {
            if (bomDataList.isEmpty()) {
                logger.info("未找到 BOM 数据");
                return new BomImportResponse(
                        new BomImportResponse.BomImportResult(0, 0, 0, new ArrayList<>()),
                        new BomImportResponse.BomItemImportResult(0, 0, 0, new ArrayList<>())
                );
            }

            long startTime = System.currentTimeMillis();

            Map<BomHeader, List<BomItemData>> bomGroups = new LinkedHashMap<>();
            for (BomData data : bomDataList) {
                bomGroups.computeIfAbsent(data.header(), k -> new ArrayList<>()).add(data.item());
            }

            int totalBomCount = bomGroups.size();
            int totalItemCount = bomDataList.size();
            logger.info("解析到 {} 个 BOM，{} 条明细，开始导入", totalBomCount, totalItemCount);

            List<BomImportResponse.ImportError> bomErrors = new ArrayList<>();
            List<BomImportResponse.ImportError> itemErrors = new ArrayList<>();
            AtomicInteger bomSuccessCount = new AtomicInteger(0);
            AtomicInteger itemSuccessCount = new AtomicInteger(0);

            Map<String, Material> materialCache = new HashMap<>();
            Map<String, Unit> unitCache = new HashMap<>();
            preloadMaterialsAndUnits(bomGroups, materialCache, unitCache);

            Map<String, BillOfMaterial> existingBomMap = preloadExistingBoms(bomGroups, materialCache);

            List<Map.Entry<BomHeader, List<BomItemData>>> bomList = new ArrayList<>(bomGroups.entrySet());

            for (int i = 0; i < bomList.size(); i += BOM_BATCH_SIZE) {
                int end = Math.min(i + BOM_BATCH_SIZE, bomList.size());
                List<Map.Entry<BomHeader, List<BomItemData>>> batch = bomList.subList(i, end);
                int batchIndex = (i / BOM_BATCH_SIZE) + 1;
                int totalBatches = (bomList.size() + BOM_BATCH_SIZE - 1) / BOM_BATCH_SIZE;

                logger.info("处理 BOM 批次 {}/{}，数量 {}", batchIndex, totalBatches, batch.size());
                long batchStart = System.currentTimeMillis();

                try {
                    BatchImportResult result = transactionTemplate.execute(status ->
                            importBatchBoms(batch, materialCache, unitCache, existingBomMap, itemErrors));
                    if (result != null) {
                        bomSuccessCount.addAndGet(result.bomSuccessCount());
                        itemSuccessCount.addAndGet(result.itemSuccessCount());
                    }
                } catch (Exception e) {
                    logger.error("BOM 批次 {} 导入失败", batchIndex, e);
                    for (Map.Entry<BomHeader, List<BomItemData>> entry : batch) {
                        if (bomErrors.size() < MAX_ERROR_COUNT) {
                            bomErrors.add(new BomImportResponse.ImportError(
                                    "BOM", entry.getKey().rowNumber(), null,
                                    "批次导入失败: " + e.getMessage()));
                        }
                    }
                }

                long batchDuration = System.currentTimeMillis() - batchStart;
                logger.info("批次 {}/{} 完成，耗时 {}ms，平均 {}ms/BOM",
                        batchIndex, totalBatches, batchDuration,
                        batch.isEmpty() ? 0 : batchDuration / batch.size());
            }

            long totalDuration = System.currentTimeMillis() - startTime;
            logger.info("BOM 导入完成，总耗时 {}ms，BOM 成功 {} / {}，BOM 明细成功 {} / {}",
                    totalDuration,
                    bomSuccessCount.get(), totalBomCount,
                    itemSuccessCount.get(), totalItemCount);

            return new BomImportResponse(
                    new BomImportResponse.BomImportResult(
                            totalBomCount,
                            bomSuccessCount.get(),
                            totalBomCount - bomSuccessCount.get(),
                            bomErrors),
                    new BomImportResponse.BomItemImportResult(
                            totalItemCount,
                            itemSuccessCount.get(),
                            totalItemCount - itemSuccessCount.get(),
                            itemErrors)
            );
        }

        private BatchImportResult importBatchBoms(
                List<Map.Entry<BomHeader, List<BomItemData>>> batch,
                Map<String, Material> materialCache,
                Map<String, Unit> unitCache,
                Map<String, BillOfMaterial> existingBomMap,
                List<BomImportResponse.ImportError> itemErrors) {

            int bomSuccessCount = 0;
            int itemSuccessCount = 0;

            // 在事务内重新初始化所有缓存实体的懒加载字段
            // 因为预加载是在事务外进行的，实体可能已经脱离了原来的 session
            initializeCacheEntities(materialCache, unitCache);

            List<BillOfMaterial> bomsToSave = new ArrayList<>();
            Map<BillOfMaterial, List<BomItem>> bomItemsMap = new LinkedHashMap<>();

            for (Map.Entry<BomHeader, List<BomItemData>> entry : batch) {
                BomHeader header = entry.getKey();
                List<BomItemData> items = entry.getValue();

                try {
                    Material parent = materialCache.get(header.materialCode());
                    if (parent == null) {
                        logger.warn("父项物料不存在: {}", header.materialCode());
                        continue;
                    }

                    String version = header.version() != null ? header.version() : "V000";
                    String bomKey = parent.getId() + ":" + version;
                    BillOfMaterial bom = existingBomMap.get(bomKey);

                    if (bom == null) {
                        BillOfMaterial.BillOfMaterialBuilder builder = BillOfMaterial.builder()
                                .material(parent)
                                .name(header.name())
                                .category(header.category())
                                .usage(header.usage())
                                .description(header.description());
                        if (header.version() != null) {
                            builder.version(header.version());
                        }
                        bom = builder.build();
                        bomsToSave.add(bom);
                    } else {
                        bomItemRepository.deleteByBomId(bom.getId());
                    }

                    List<BomItem> bomItems = new ArrayList<>();
                    int sequence = 1;

                    for (BomItemData itemData : items) {
                        try {
                            Material childMaterial = materialCache.get(itemData.childMaterialCode());
                            if (childMaterial == null) {
                                addItemError(itemErrors, itemData.rowNumber(), "FMATERIALIDCHILD",
                                        "子项物料不存在: " + itemData.childMaterialCode());
                                continue;
                            }

                            // 确保 childMaterial 的懒加载字段已初始化（在事务内）
                            // 必须在访问 childMaterial.getBaseUnit() 之前初始化
                            if (childMaterial.getMaterialGroup() != null) {
                                childMaterial.getMaterialGroup().getId();
                                childMaterial.getMaterialGroup().getCode();
                                childMaterial.getMaterialGroup().getName();
                            }
                            if (childMaterial.getBaseUnit() != null) {
                                Unit baseUnit = childMaterial.getBaseUnit();
                                baseUnit.getId();
                                if (baseUnit.getUnitGroup() != null) {
                                    baseUnit.getUnitGroup().getId();
                                    baseUnit.getUnitGroup().getCode();
                                    baseUnit.getUnitGroup().getName();
                                }
                            }

                            Unit childUnit;
                            if (itemData.childUnitCode() != null) {
                                childUnit = unitCache.get(itemData.childUnitCode());
                                if (childUnit == null) {
                                    addItemError(itemErrors, itemData.rowNumber(), "FCHILDUNITID",
                                            "子项单位不存在: " + itemData.childUnitCode());
                                    continue;
                                }
                                // 确保从缓存获取的 Unit 的懒加载字段已初始化（在事务内）
                                if (childUnit.getUnitGroup() != null) {
                                    childUnit.getUnitGroup().getId();
                                    childUnit.getUnitGroup().getCode();
                                    childUnit.getUnitGroup().getName();
                                }
                            } else {
                                childUnit = childMaterial.getBaseUnit();
                                // 确保 baseUnit 的懒加载字段已初始化（在事务内）
                                if (childUnit != null && childUnit.getUnitGroup() != null) {
                                    childUnit.getUnitGroup().getId();
                                    childUnit.getUnitGroup().getCode();
                                    childUnit.getUnitGroup().getName();
                                }
                            }

                            Integer seq = itemData.sequence() != null ? itemData.sequence() : sequence++;
                            BigDecimal numerator = parseDecimalOrDefault(itemData.numerator(), BigDecimal.ONE);
                            BigDecimal denominator = parseDecimalOrDefault(itemData.denominator(), BigDecimal.ONE);
                            BigDecimal scrapRate = parseDecimalOrNull(itemData.scrapRate());
                            String childBomVersion = itemData.childBomVersion();

                            BomItem bomItem = BomItem.builder()
                                    .bom(bom)
                                    .sequence(seq)
                                    .childMaterial(childMaterial)
                                    .childUnit(childUnit)
                                    .numerator(numerator)
                                    .denominator(denominator)
                                    .scrapRate(scrapRate)
                                    .childBomVersion(childBomVersion)
                                    .memo(itemData.memo())
                                    .build();
                            bomItems.add(bomItem);
                        } catch (Exception e) {
                            logger.error("导入 BOM 明细失败，行 {}", itemData.rowNumber(), e);
                            addItemError(itemErrors, itemData.rowNumber(), null, "导入失败: " + e.getMessage());
                        }
                    }

                    if (!bomItems.isEmpty()) {
                        // 在放入 HashMap 之前，确保所有相关实体的懒加载字段都已初始化
                        // 因为 HashMap.put() 会调用 hashCode()，而 hashCode() 会访问所有字段
                        // Material -> baseUnit -> unitGroup 的链式访问需要在事务内完成
                        if (bom.getMaterial() != null) {
                            Material material = bom.getMaterial();
                            // 初始化 Material 的懒加载字段
                            if (material.getMaterialGroup() != null) {
                                material.getMaterialGroup().getId();
                                material.getMaterialGroup().getCode();
                            }
                            if (material.getBaseUnit() != null) {
                                Unit baseUnit = material.getBaseUnit();
                                baseUnit.getId();
                                // 初始化 Unit 的懒加载字段
                                if (baseUnit.getUnitGroup() != null) {
                                    baseUnit.getUnitGroup().getId();
                                    baseUnit.getUnitGroup().getCode();
                                }
                            }
                        }
                        bomItemsMap.put(bom, bomItems);
                    }
                    bomSuccessCount++;
                } catch (Exception e) {
                    logger.error("导入 BOM 失败，物料={} 版本={}", header.materialCode(), header.version(), e);
                }
            }

            if (!bomsToSave.isEmpty()) {
                bomRepository.saveAll(bomsToSave);
            }

            List<BomItem> allItems = new ArrayList<>();
            for (List<BomItem> items : bomItemsMap.values()) {
                allItems.addAll(items);
            }
            if (!allItems.isEmpty()) {
                bomItemRepository.saveAll(allItems);
                itemSuccessCount = allItems.size();
            }

            return new BatchImportResult(bomSuccessCount, itemSuccessCount);
        }

        /**
         * 在事务内初始化所有缓存实体的懒加载字段
         * 因为预加载是在事务外进行的，实体可能已经脱离了原来的 session
         * 解决方案：在事务内重新查询这些实体，确保它们在当前 session 中
         */
        private void initializeCacheEntities(Map<String, Material> materialCache, Map<String, Unit> unitCache) {
            // 在事务内重新查询 Material，确保它们在当前 session 中
            if (!materialCache.isEmpty()) {
                Set<String> materialCodes = new HashSet<>(materialCache.keySet());
                List<String> materialCodeList = new ArrayList<>(materialCodes);
                for (int i = 0; i < materialCodeList.size(); i += BATCH_QUERY_CHUNK_SIZE) {
                    int end = Math.min(i + BATCH_QUERY_CHUNK_SIZE, materialCodeList.size());
                    List<String> chunk = materialCodeList.subList(i, end);
                    // 在事务内重新查询，确保实体在当前 session 中
                    List<Material> materials = materialRepository.findByCodeInWithMaterialGroup(chunk);
                    for (Material material : materials) {
                        // 替换缓存中的实体，使用当前 session 中的实体
                        materialCache.put(material.getCode(), material);
                    }
                }
            }
            
            // 在事务内重新查询 Unit，确保它们在当前 session 中
            if (!unitCache.isEmpty()) {
                Set<String> unitCodes = new HashSet<>(unitCache.keySet());
                List<String> unitCodeList = new ArrayList<>(unitCodes);
                for (int i = 0; i < unitCodeList.size(); i += BATCH_QUERY_CHUNK_SIZE) {
                    int end = Math.min(i + BATCH_QUERY_CHUNK_SIZE, unitCodeList.size());
                    List<String> chunk = unitCodeList.subList(i, end);
                    // 在事务内重新查询，确保实体在当前 session 中
                    List<Unit> units = unitRepository.findByCodeInWithUnitGroup(chunk);
                    for (Unit unit : units) {
                        // 替换缓存中的实体，使用当前 session 中的实体
                        unitCache.put(unit.getCode(), unit);
                    }
                }
            }
        }

        private void preloadMaterialsAndUnits(
                Map<BomHeader, List<BomItemData>> bomGroups,
                Map<String, Material> materialCache,
                Map<String, Unit> unitCache) {

            Set<String> materialCodes = new HashSet<>();
            Set<String> unitCodes = new HashSet<>();

            for (Map.Entry<BomHeader, List<BomItemData>> entry : bomGroups.entrySet()) {
                materialCodes.add(entry.getKey().materialCode());
                for (BomItemData item : entry.getValue()) {
                    materialCodes.add(item.childMaterialCode());
                    if (item.childUnitCode() != null) {
                        unitCodes.add(item.childUnitCode());
                    }
                }
            }

            List<String> materialCodeList = new ArrayList<>(materialCodes);
            for (int i = 0; i < materialCodeList.size(); i += BATCH_QUERY_CHUNK_SIZE) {
                int end = Math.min(i + BATCH_QUERY_CHUNK_SIZE, materialCodeList.size());
                List<String> chunk = materialCodeList.subList(i, end);
                // 使用 JOIN FETCH 预加载 MaterialGroup 和 baseUnit，避免 LazyInitializationException
                // 当 Material 被放入 HashMap 时，hashCode() 会访问 MaterialGroup 和 baseUnit
                List<Material> materials = materialRepository.findByCodeInWithMaterialGroup(chunk);
                for (Material m : materials) {
                    // 注意：预加载是在事务外进行的，所以这里的初始化可能不够
                    // 真正的初始化会在 importBatchBoms 的 initializeCacheEntities 中进行
                    materialCache.put(m.getCode(), m);
                }
            }

            if (!unitCodes.isEmpty()) {
                List<String> unitCodeList = new ArrayList<>(unitCodes);
                for (int i = 0; i < unitCodeList.size(); i += BATCH_QUERY_CHUNK_SIZE) {
                    int end = Math.min(i + BATCH_QUERY_CHUNK_SIZE, unitCodeList.size());
                    List<String> chunk = unitCodeList.subList(i, end);
                    // 使用 JOIN FETCH 预加载 UnitGroup，避免 LazyInitializationException
                    List<Unit> units = unitRepository.findByCodeInWithUnitGroup(chunk);
                    for (Unit u : units) {
                        // 确保 UnitGroup 完全初始化（在事务内）
                        // 访问多个字段以确保代理对象被完全初始化
                        // 注意：虽然使用了 JOIN FETCH，但 UnitGroup 可能仍然是代理对象
                        if (u.getUnitGroup() != null) {
                            u.getUnitGroup().getId();
                            u.getUnitGroup().getCode(); // 触发代理初始化
                            u.getUnitGroup().getName(); // 进一步确保初始化
                        }
                        unitCache.put(u.getCode(), u);
                    }
                }
            }
        }

        private Map<String, BillOfMaterial> preloadExistingBoms(
                Map<BomHeader, List<BomItemData>> bomGroups,
                Map<String, Material> materialCache) {

            Map<String, BillOfMaterial> existingBomMap = new HashMap<>();
            Set<Long> materialIds = new HashSet<>();

            for (BomHeader header : bomGroups.keySet()) {
                Material material = materialCache.get(header.materialCode());
                if (material != null) {
                    materialIds.add(material.getId());
                }
            }

            List<Long> materialIdList = new ArrayList<>(materialIds);
            for (int i = 0; i < materialIdList.size(); i += BATCH_QUERY_CHUNK_SIZE) {
                int end = Math.min(i + BATCH_QUERY_CHUNK_SIZE, materialIdList.size());
                List<Long> chunk = materialIdList.subList(i, end);
                List<BillOfMaterial> boms = bomRepository.findByMaterialIdIn(chunk);
                for (BillOfMaterial bom : boms) {
                    String key = bom.getMaterial().getId() + ":" + bom.getVersion();
                    existingBomMap.put(key, bom);
                }
            }

            return existingBomMap;
        }

        private void addItemError(List<BomImportResponse.ImportError> itemErrors,
                                  int rowNumber,
                                  String field,
                                  String message) {
            if (itemErrors.size() < MAX_ERROR_COUNT) {
                itemErrors.add(new BomImportResponse.ImportError("BOM明细", rowNumber, field, message));
            }
        }

        private Integer parseInteger(String value) {
            if (value == null || value.trim().isEmpty()) {
                return null;
            }
            try {
                return Integer.parseInt(value.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }

        private BigDecimal parseDecimalOrDefault(String value, BigDecimal defaultValue) {
            if (value == null || value.trim().isEmpty()) {
                return defaultValue;
            }
            try {
                return new BigDecimal(value.trim().replace(",", ""));
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }

        private BigDecimal parseDecimalOrNull(String value) {
            if (value == null || value.trim().isEmpty()) {
                return null;
            }
            try {
                return new BigDecimal(value.trim().replace(",", ""));
            } catch (NumberFormatException e) {
                return null;
            }
        }

        private String trimToNull(String value) {
            if (value == null) {
                return null;
            }
            String trimmed = value.trim();
            return trimmed.isEmpty() ? null : trimmed;
        }

        private record BomHeader(
                int rowNumber,
                String materialCode,
                String version,
                String name,
                String category,
                String usage,
                String description
        ) {
        }

        private record BomItemData(
                int rowNumber,
                Integer sequence,
                String childMaterialCode,
                String childUnitCode,
                String numerator,
                String denominator,
                String scrapRate,
                String childBomVersion,
                String memo
        ) {
        }

        private record BomData(
                BomHeader header,
                BomItemData item
        ) {
        }

        private record BatchImportResult(
                int bomSuccessCount,
                int itemSuccessCount
        ) {
        }
    }
}

