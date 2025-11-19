package com.sambound.erp.service.importing.bom;

import cn.idev.excel.FastExcel;
import cn.idev.excel.context.AnalysisContext;
import cn.idev.excel.read.listener.ReadListener;
import com.sambound.erp.config.ImportConfiguration;
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
import com.sambound.erp.service.importing.exception.ImportProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayInputStream;
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

    private final BillOfMaterialRepository bomRepository;
    private final BomItemRepository bomItemRepository;
    private final MaterialRepository materialRepository;
    private final UnitRepository unitRepository;
    private final TransactionTemplate transactionTemplate;
    private final ImportConfiguration importConfig;

    public BomImportProcessor(
            BillOfMaterialRepository bomRepository,
            BomItemRepository bomItemRepository,
            MaterialRepository materialRepository,
            UnitRepository unitRepository,
            TransactionTemplate transactionTemplate,
            ImportConfiguration importConfig) {
        this.bomRepository = bomRepository;
        this.bomItemRepository = bomItemRepository;
        this.materialRepository = materialRepository;
        this.unitRepository = unitRepository;
        this.transactionTemplate = transactionTemplate;
        this.importConfig = importConfig;
    }

    public BomImportResponse process(byte[] fileBytes, String fileName) {
        try {
            BomDataCollector collector = new BomDataCollector(importConfig);
            FastExcel.read(new ByteArrayInputStream(fileBytes), BomExcelRow.class, collector)
                    .sheet("物料清单#单据头(FBillHead)")
                    .headRowNumber(2)
                    .doRead();
            return collector.importToDatabase(fileName);
        } catch (Exception e) {
            logger.error("BOM Excel 导入失败", e);
            throw new ImportProcessingException("BOM Excel 导入失败: " + e.getMessage(), e);
        }
    }

    private class BomDataCollector implements ReadListener<BomExcelRow> {

        private final List<BomData> bomDataList = new ArrayList<>();
        private final AtomicInteger totalRows = new AtomicInteger(0);
        private final ImportConfiguration importConfig;
        private BomHeader currentHeader = null;

        public BomDataCollector(ImportConfiguration importConfig) {
            this.importConfig = importConfig;
        }

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

        public BomImportResponse importToDatabase(String fileName) {
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
            int batchSize = importConfig.getBatch().getInsertSize();

            for (int i = 0; i < bomList.size(); i += batchSize) {
                int end = Math.min(i + batchSize, bomList.size());
                List<Map.Entry<BomHeader, List<BomItemData>>> batch = bomList.subList(i, end);
                int batchIndex = (i / batchSize) + 1;
                int totalBatches = (bomList.size() + batchSize - 1) / batchSize;

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
                        if (bomErrors.size() < importConfig.getError().getMaxErrorCount()) {
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
            logger.info("BOM 导入完成 [{}]，总耗时 {}ms，BOM 成功 {} / {}，BOM 明细成功 {} / {}",
                    fileName,
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

                            Unit childUnit;
                            if (itemData.childUnitCode() != null) {
                                childUnit = unitCache.get(itemData.childUnitCode());
                                if (childUnit == null) {
                                    addItemError(itemErrors, itemData.rowNumber(), "FCHILDUNITID",
                                            "子项单位不存在: " + itemData.childUnitCode());
                                    continue;
                                }
                            } else {
                                childUnit = childMaterial.getBaseUnit();
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
            int queryChunkSize = importConfig.getBatch().getQueryChunkSize();
            for (int i = 0; i < materialCodeList.size(); i += queryChunkSize) {
                int end = Math.min(i + queryChunkSize, materialCodeList.size());
                List<String> chunk = materialCodeList.subList(i, end);
                materialRepository.findByCodeIn(chunk).forEach(m -> materialCache.put(m.getCode(), m));
            }

            if (!unitCodes.isEmpty()) {
                List<String> unitCodeList = new ArrayList<>(unitCodes);
                for (int i = 0; i < unitCodeList.size(); i += queryChunkSize) {
                    int end = Math.min(i + queryChunkSize, unitCodeList.size());
                    List<String> chunk = unitCodeList.subList(i, end);
                    unitRepository.findByCodeIn(chunk).forEach(u -> unitCache.put(u.getCode(), u));
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
            int queryChunkSize = importConfig.getBatch().getQueryChunkSize();
            for (int i = 0; i < materialIdList.size(); i += queryChunkSize) {
                int end = Math.min(i + queryChunkSize, materialIdList.size());
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
            if (itemErrors.size() < importConfig.getError().getMaxErrorCount()) {
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
