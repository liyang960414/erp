package com.sambound.erp.service;

import cn.idev.excel.FastExcel;
import cn.idev.excel.context.AnalysisContext;
import cn.idev.excel.read.listener.ReadListener;
import com.sambound.erp.dto.BomExcelRow;
import com.sambound.erp.dto.BomImportResponse;
import com.sambound.erp.entity.BillOfMaterial;
import com.sambound.erp.entity.BomItem;
import com.sambound.erp.entity.Material;
import com.sambound.erp.entity.Unit;
import com.sambound.erp.repository.BillOfMaterialRepository;
import com.sambound.erp.repository.BomItemRepository;
import com.sambound.erp.repository.MaterialRepository;
import com.sambound.erp.repository.UnitRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class BomImportService {

    private static final Logger logger = LoggerFactory.getLogger(BomImportService.class);
    private static final int MAX_ERROR_COUNT = 1000;
    private static final int BATCH_QUERY_CHUNK_SIZE = 1000;
    private static final int BOM_BATCH_SIZE = 500; // 每批处理的BOM数量

    private final BillOfMaterialRepository bomRepository;
    private final BomItemRepository bomItemRepository;
    private final MaterialRepository materialRepository;
    private final UnitRepository unitRepository;
    private final TransactionTemplate transactionTemplate;

    public BomImportService(
            BillOfMaterialRepository bomRepository,
            BomItemRepository bomItemRepository,
            MaterialRepository materialRepository,
            UnitRepository unitRepository,
            PlatformTransactionManager transactionManager) {
        this.bomRepository = bomRepository;
        this.bomItemRepository = bomItemRepository;
        this.materialRepository = materialRepository;
        this.unitRepository = unitRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
        this.transactionTemplate.setTimeout(120);
    }

    public BomImportResponse importFromExcel(MultipartFile file) {
        logger.info("开始导入BOM Excel文件: {}", file.getOriginalFilename());

        try {
            byte[] fileBytes = file.getBytes();

            BomDataCollector collector = new BomDataCollector();
            FastExcel.read(new ByteArrayInputStream(fileBytes), BomExcelRow.class, collector)
                    .sheet("物料清单#单据头(FBillHead)")
                    .headRowNumber(2)  // 前两行为表头
                    .doRead();

            BomImportResponse result = collector.importToDatabase();
            logger.info("BOM导入完成：BOM总计 {} 条，成功 {} 条，失败 {} 条；明细总计 {} 条，成功 {} 条，失败 {} 条",
                    result.bomResult().totalRows(), result.bomResult().successCount(), result.bomResult().failureCount(),
                    result.itemResult().totalRows(), result.itemResult().successCount(), result.itemResult().failureCount());

            return result;
        } catch (Exception e) {
            logger.error("Excel文件导入失败", e);
            throw new RuntimeException("Excel文件导入失败: " + e.getMessage(), e);
        }
    }

    /**
     * BOM数据收集器：处理CSV的特殊结构（父项字段只在第一行有值，后续行重复）
     */
    private class BomDataCollector implements ReadListener<BomExcelRow> {
        private final List<BomData> bomDataList = new ArrayList<>();
        private final AtomicInteger totalRows = new AtomicInteger(0);
        private BomHeader currentHeader = null;

        @Override
        public void invoke(BomExcelRow data, AnalysisContext context) {
            totalRows.incrementAndGet();
            int rowNum = context.readRowHolder().getRowIndex();

            // 检查是否是新的BOM头（billHead不为空，或者父项物料编码不为空）
            if ((data.getBillHead() != null && !data.getBillHead().trim().isEmpty()) ||
                (data.getMaterialCode() != null && !data.getMaterialCode().trim().isEmpty())) {
                // 创建新的BOM头
                // 版本可以为空，如果为空则使用实体类的默认值
                String version = null;
                if (data.getVersion() != null && !data.getVersion().trim().isEmpty()) {
                    version = data.getVersion().trim();
                }
                currentHeader = new BomHeader(
                        rowNum,
                        data.getMaterialCode() != null ? data.getMaterialCode().trim() : null,
                        version,
                        data.getName(),
                        data.getCategory(),
                        data.getUsage(),
                        data.getDescription()
                );
            }

            // 如果有BOM头且子项物料编码不为空，添加明细项
            if (currentHeader != null && 
                data.getChildMaterialCode() != null && !data.getChildMaterialCode().trim().isEmpty()) {
                
                BomData bomData = new BomData(currentHeader, new BomItemData(
                        rowNum,
                        data.getSequence() != null && !data.getSequence().trim().isEmpty() 
                                ? Integer.parseInt(data.getSequence().trim()) : null,
                        data.getChildMaterialCode().trim(),
                        data.getChildUnitCode() != null && !data.getChildUnitCode().trim().isEmpty() 
                                ? data.getChildUnitCode().trim() : null,
                        data.getNumerator(),
                        data.getDenominator(),
                        data.getScrapRate(),
                        data.getChildBomVersion(),
                        data.getMemo()
                ));
                bomDataList.add(bomData);
            }
        }

        @Override
        public void doAfterAllAnalysed(AnalysisContext context) {
            logger.info("BOM数据收集完成，共 {} 条BOM明细数据", bomDataList.size());
        }

        public BomImportResponse importToDatabase() {
            if (bomDataList.isEmpty()) {
                logger.info("未找到BOM数据");
                return new BomImportResponse(
                        new BomImportResponse.BomImportResult(0, 0, 0, new ArrayList<>()),
                        new BomImportResponse.BomItemImportResult(0, 0, 0, new ArrayList<>())
                );
            }

            long startTime = System.currentTimeMillis();

            // 按BOM头分组
            Map<BomHeader, List<BomItemData>> bomGroups = new LinkedHashMap<>();
            for (BomData data : bomDataList) {
                bomGroups.computeIfAbsent(data.header, k -> new ArrayList<>()).add(data.item);
            }

            int totalBomCount = bomGroups.size();
            int totalItemCount = bomDataList.size();
            logger.info("找到 {} 个BOM，{} 条明细，开始导入到数据库", totalBomCount, totalItemCount);

            List<BomImportResponse.ImportError> bomErrors = new ArrayList<>();
            List<BomImportResponse.ImportError> itemErrors = new ArrayList<>();
            AtomicInteger bomSuccessCount = new AtomicInteger(0);
            AtomicInteger itemSuccessCount = new AtomicInteger(0);

            // 预加载物料和单位数据
            Map<String, Material> materialCache = new HashMap<>();
            Map<String, Unit> unitCache = new HashMap<>();
            preloadMaterialsAndUnits(bomGroups, materialCache, unitCache);

            // 预先批量查询所有已存在的BOM
            Map<String, BillOfMaterial> existingBomMap = preloadExistingBoms(bomGroups, materialCache);

            // 将BOM分组转换为列表以便批量处理
            List<Map.Entry<BomHeader, List<BomItemData>>> bomList = new ArrayList<>(bomGroups.entrySet());

            // 批量导入BOM
            for (int i = 0; i < bomList.size(); i += BOM_BATCH_SIZE) {
                int end = Math.min(i + BOM_BATCH_SIZE, bomList.size());
                List<Map.Entry<BomHeader, List<BomItemData>>> batch = bomList.subList(i, end);
                int batchIndex = (i / BOM_BATCH_SIZE) + 1;
                int totalBatches = (bomList.size() + BOM_BATCH_SIZE - 1) / BOM_BATCH_SIZE;

                logger.info("处理批次 {}/{}，BOM数量: {}", batchIndex, totalBatches, batch.size());
                long batchStartTime = System.currentTimeMillis();

                // 每个批次使用独立事务
                try {
                    BatchImportResult result = transactionTemplate.execute(status -> {
                        return importBatchBoms(batch, materialCache, unitCache, existingBomMap, itemErrors);
                    });

                    if (result != null) {
                        bomSuccessCount.addAndGet(result.bomSuccessCount);
                        itemSuccessCount.addAndGet(result.itemSuccessCount);
                    }
                } catch (Exception e) {
                    logger.error("批次 {} 导入失败", batchIndex, e);
                    // 记录批次级别的错误
                    for (Map.Entry<BomHeader, List<BomItemData>> entry : batch) {
                        if (bomErrors.size() < MAX_ERROR_COUNT) {
                            bomErrors.add(new BomImportResponse.ImportError(
                                    "BOM", entry.getKey().rowNumber, null,
                                    "批次导入失败: " + e.getMessage()));
                        }
                    }
                }

                long batchDuration = System.currentTimeMillis() - batchStartTime;
                logger.info("批次 {}/{} 完成，耗时: {}ms，平均: {}ms/BOM",
                        batchIndex, totalBatches, batchDuration,
                        batch.size() > 0 ? batchDuration / batch.size() : 0);
            }

            long totalDuration = System.currentTimeMillis() - startTime;
            logger.info("BOM导入完成：总耗时 {}ms，BOM总计 {} 条，成功 {} 条，失败 {} 条；明细总计 {} 条，成功 {} 条，失败 {} 条",
                    totalDuration, totalBomCount, bomSuccessCount.get(), totalBomCount - bomSuccessCount.get(),
                    totalItemCount, itemSuccessCount.get(), totalItemCount - itemSuccessCount.get());

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

        private void preloadMaterialsAndUnits(
                Map<BomHeader, List<BomItemData>> bomGroups,
                Map<String, Material> materialCache,
                Map<String, Unit> unitCache) {

            Set<String> materialCodes = new HashSet<>();
            Set<String> unitCodes = new HashSet<>();

            // 收集所有物料编码和单位编码
            for (Map.Entry<BomHeader, List<BomItemData>> entry : bomGroups.entrySet()) {
                materialCodes.add(entry.getKey().materialCode);
                for (BomItemData item : entry.getValue()) {
                    materialCodes.add(item.childMaterialCode);
                    if (item.childUnitCode != null) {
                        unitCodes.add(item.childUnitCode);
                    }
                }
            }

            // 批量查询物料
            List<String> materialCodeList = new ArrayList<>(materialCodes);
            for (int i = 0; i < materialCodeList.size(); i += BATCH_QUERY_CHUNK_SIZE) {
                int end = Math.min(i + BATCH_QUERY_CHUNK_SIZE, materialCodeList.size());
                List<String> chunk = materialCodeList.subList(i, end);
                materialRepository.findByCodeIn(chunk).forEach(m -> materialCache.put(m.getCode(), m));
            }

            // 批量查询单位
            if (!unitCodes.isEmpty()) {
                List<String> unitCodeList = new ArrayList<>(unitCodes);
                for (int i = 0; i < unitCodeList.size(); i += BATCH_QUERY_CHUNK_SIZE) {
                    int end = Math.min(i + BATCH_QUERY_CHUNK_SIZE, unitCodeList.size());
                    List<String> chunk = unitCodeList.subList(i, end);
                    unitRepository.findByCodeIn(chunk).forEach(u -> unitCache.put(u.getCode(), u));
                }
            }

            logger.debug("预加载完成：物料 {} 个，单位 {} 个", materialCache.size(), unitCache.size());
        }

        /**
         * 预先批量查询所有已存在的BOM
         */
        private Map<String, BillOfMaterial> preloadExistingBoms(
                Map<BomHeader, List<BomItemData>> bomGroups,
                Map<String, Material> materialCache) {

            Map<String, BillOfMaterial> existingBomMap = new HashMap<>();
            
            // 收集所有需要查询的(materialId, version)组合
            Set<String> bomKeys = new HashSet<>();
            for (Map.Entry<BomHeader, List<BomItemData>> entry : bomGroups.entrySet()) {
                BomHeader header = entry.getKey();
                Material material = materialCache.get(header.materialCode);
                if (material != null) {
                    String versionToUse = header.version != null ? header.version : "V000";
                    String bomKey = material.getId() + ":" + versionToUse;
                    bomKeys.add(bomKey);
                }
            }

            if (bomKeys.isEmpty()) {
                return existingBomMap;
            }

            // 收集所有物料ID
            Set<Long> materialIds = new HashSet<>();
            for (Map.Entry<BomHeader, List<BomItemData>> entry : bomGroups.entrySet()) {
                BomHeader header = entry.getKey();
                Material material = materialCache.get(header.materialCode);
                if (material != null) {
                    materialIds.add(material.getId());
                }
            }

            // 批量查询所有相关BOM（按物料ID批量查询）
            List<Long> materialIdList = new ArrayList<>(materialIds);
            for (int i = 0; i < materialIdList.size(); i += BATCH_QUERY_CHUNK_SIZE) {
                int end = Math.min(i + BATCH_QUERY_CHUNK_SIZE, materialIdList.size());
                List<Long> chunk = materialIdList.subList(i, end);
                List<BillOfMaterial> boms = bomRepository.findByMaterialIdIn(chunk);
                
                // 建立映射
                for (BillOfMaterial bom : boms) {
                    String bomKey = bom.getMaterial().getId() + ":" + bom.getVersion();
                    existingBomMap.put(bomKey, bom);
                }
            }

            logger.debug("预加载已存在的BOM: {} 个", existingBomMap.size());
            return existingBomMap;
        }

        /**
         * 批量导入BOM（在事务内执行）
         */
        private BatchImportResult importBatchBoms(
                List<Map.Entry<BomHeader, List<BomItemData>>> batch,
                Map<String, Material> materialCache,
                Map<String, Unit> unitCache,
                Map<String, BillOfMaterial> existingBomMap,
                List<BomImportResponse.ImportError> itemErrors) {

            int bomSuccessCount = 0;
            int itemSuccessCount = 0;
            
            // 收集所有需要保存的BOM和明细项
            List<BillOfMaterial> bomsToSave = new ArrayList<>();
            Map<BillOfMaterial, List<BomItem>> bomItemsMap = new LinkedHashMap<>();
            
            for (Map.Entry<BomHeader, List<BomItemData>> entry : batch) {
                BomHeader header = entry.getKey();
                List<BomItemData> items = entry.getValue();
                
                try {
                    // 验证父项物料
                    Material parentMaterial = materialCache.get(header.materialCode);
                    if (parentMaterial == null) {
                        logger.warn("父项物料不存在: {}", header.materialCode);
                        continue;
                    }

                    // 检查BOM是否已存在
                    String versionToUse = header.version != null ? header.version : "V000";
                    String bomKey = parentMaterial.getId() + ":" + versionToUse;
                    BillOfMaterial bom = existingBomMap.get(bomKey);
                    
                    if (bom == null) {
                        // 创建新的BOM
                        BillOfMaterial.BillOfMaterialBuilder builder = BillOfMaterial.builder()
                                .material(parentMaterial)
                                .name(header.name)
                                .category(header.category)
                                .usage(header.usage)
                                .description(header.description);
                        if (header.version != null) {
                            builder.version(header.version);
                        }
                        bom = builder.build();
                        bomsToSave.add(bom);
                    } else {
                        // 删除现有明细项（如果需要更新）
                        bomItemRepository.deleteByBomId(bom.getId());
                    }

                    // 处理明细项
                    List<BomItem> bomItems = new ArrayList<>();
                    int sequence = 1;
                    
                    for (BomItemData itemData : items) {
                        try {
                            Material childMaterial = materialCache.get(itemData.childMaterialCode);
                            if (childMaterial == null) {
                                if (itemErrors.size() < MAX_ERROR_COUNT) {
                                    itemErrors.add(new BomImportResponse.ImportError(
                                            "BOM明细", itemData.rowNumber, "FMATERIALIDCHILD",
                                            "子项物料不存在: " + itemData.childMaterialCode));
                                }
                                continue;
                            }

                            Unit childUnit;
                            if (itemData.childUnitCode != null) {
                                childUnit = unitCache.get(itemData.childUnitCode);
                                if (childUnit == null) {
                                    if (itemErrors.size() < MAX_ERROR_COUNT) {
                                        itemErrors.add(new BomImportResponse.ImportError(
                                                "BOM明细", itemData.rowNumber, "FCHILDUNITID",
                                                "子项单位不存在: " + itemData.childUnitCode));
                                    }
                                    continue;
                                }
                            } else {
                                childUnit = childMaterial.getBaseUnit();
                            }

                            Integer seq = itemData.sequence != null ? itemData.sequence : sequence++;

                            BigDecimal numerator = BigDecimal.ONE;
                            if (itemData.numerator != null && !itemData.numerator.trim().isEmpty()) {
                                try {
                                    String numeratorStr = itemData.numerator.trim().replace(",", "");
                                    numerator = new BigDecimal(numeratorStr);
                                } catch (NumberFormatException e) {
                                    logger.warn("用量分子格式错误: {}, 使用默认值1", itemData.numerator);
                                }
                            }

                            BigDecimal denominator = BigDecimal.ONE;
                            if (itemData.denominator != null && !itemData.denominator.trim().isEmpty()) {
                                try {
                                    String denominatorStr = itemData.denominator.trim().replace(",", "");
                                    denominator = new BigDecimal(denominatorStr);
                                } catch (NumberFormatException e) {
                                    logger.warn("用量分母格式错误: {}, 使用默认值1", itemData.denominator);
                                }
                            }

                            BigDecimal scrapRate = null;
                            if (itemData.scrapRate != null && !itemData.scrapRate.trim().isEmpty()) {
                                try {
                                    String scrapRateStr = itemData.scrapRate.trim().replace(",", "");
                                    scrapRate = new BigDecimal(scrapRateStr);
                                } catch (NumberFormatException e) {
                                    logger.warn("损耗率格式错误: {}", itemData.scrapRate);
                                }
                            }

                            String childBomVersion = (itemData.childBomVersion != null && !itemData.childBomVersion.trim().isEmpty())
                                    ? itemData.childBomVersion.trim() : null;

                            BomItem bomItem = BomItem.builder()
                                    .bom(bom)
                                    .sequence(seq)
                                    .childMaterial(childMaterial)
                                    .childUnit(childUnit)
                                    .numerator(numerator)
                                    .denominator(denominator)
                                    .scrapRate(scrapRate)
                                    .childBomVersion(childBomVersion)
                                    .memo(itemData.memo)
                                    .build();

                            bomItems.add(bomItem);
                        } catch (Exception e) {
                            logger.error("导入BOM明细失败: 行{}", itemData.rowNumber, e);
                            if (itemErrors.size() < MAX_ERROR_COUNT) {
                                itemErrors.add(new BomImportResponse.ImportError(
                                        "BOM明细", itemData.rowNumber, null,
                                        "导入失败: " + e.getMessage()));
                            }
                        }
                    }

                    if (!bomItems.isEmpty()) {
                        bomItemsMap.put(bom, bomItems);
                    }
                    
                    if (bom.getId() == null) {
                        bomSuccessCount++;
                    } else {
                        bomSuccessCount++; // 更新也算成功
                    }
                } catch (Exception e) {
                    logger.error("导入BOM失败: 物料={}, 版本={}", header.materialCode, header.version, e);
                    // 错误已在itemErrors中记录
                }
            }

            // 批量保存BOM
            if (!bomsToSave.isEmpty()) {
                bomRepository.saveAll(bomsToSave);
            }

            // 批量保存所有明细项
            List<BomItem> allBomItems = new ArrayList<>();
            for (Map.Entry<BillOfMaterial, List<BomItem>> entry : bomItemsMap.entrySet()) {
                allBomItems.addAll(entry.getValue());
            }
            
            if (!allBomItems.isEmpty()) {
                bomItemRepository.saveAll(allBomItems);
                itemSuccessCount = allBomItems.size();
            }

            return new BatchImportResult(bomSuccessCount, itemSuccessCount);
        }

        /**
         * 批量导入结果
         */
        private static class BatchImportResult {
            final int bomSuccessCount;
            final int itemSuccessCount;

            BatchImportResult(int bomSuccessCount, int itemSuccessCount) {
                this.bomSuccessCount = bomSuccessCount;
                this.itemSuccessCount = itemSuccessCount;
            }
        }

    }

    private record BomHeader(
            int rowNumber,
            String materialCode,
            String version,
            String name,
            String category,
            String usage,
            String description
    ) {}

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
    ) {}

    private record BomData(
            BomHeader header,
            BomItemData item
    ) {}
}

