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
                currentHeader = new BomHeader(
                        rowNum,
                        data.getMaterialCode() != null ? data.getMaterialCode().trim() : null,
                        data.getVersion() != null && !data.getVersion().trim().isEmpty() 
                                ? data.getVersion().trim() : "V000",
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

            // 按BOM头分组
            Map<BomHeader, List<BomItemData>> bomGroups = new LinkedHashMap<>();
            for (BomData data : bomDataList) {
                bomGroups.computeIfAbsent(data.header, k -> new ArrayList<>()).add(data.item);
            }

            logger.info("找到 {} 个BOM，开始导入到数据库", bomGroups.size());

            List<BomImportResponse.ImportError> bomErrors = new ArrayList<>();
            List<BomImportResponse.ImportError> itemErrors = new ArrayList<>();
            AtomicInteger bomSuccessCount = new AtomicInteger(0);
            AtomicInteger itemSuccessCount = new AtomicInteger(0);
            AtomicInteger itemTotalCount = new AtomicInteger(0);

            // 预加载物料和单位数据
            Map<String, Material> materialCache = new HashMap<>();
            Map<String, Unit> unitCache = new HashMap<>();
            preloadMaterialsAndUnits(bomGroups, materialCache, unitCache);

            // 导入每个BOM
            for (Map.Entry<BomHeader, List<BomItemData>> entry : bomGroups.entrySet()) {
                BomHeader header = entry.getKey();
                List<BomItemData> items = entry.getValue();

                try {
                    // 验证父项物料
                    Material parentMaterial = materialCache.get(header.materialCode);
                    if (parentMaterial == null) {
                        if (bomErrors.size() < MAX_ERROR_COUNT) {
                            bomErrors.add(new BomImportResponse.ImportError(
                                    "BOM", header.rowNumber, "FMATERIALID",
                                    "父项物料不存在: " + header.materialCode));
                        }
                        continue;
                    }

                    // 验证父项物料属性：只有自制和委外类型的物料可以创建BOM
                    if (!isValidBomMaterialType(parentMaterial.getErpClsId())) {
                        if (bomErrors.size() < MAX_ERROR_COUNT) {
                            bomErrors.add(new BomImportResponse.ImportError(
                                    "BOM", header.rowNumber, "FMATERIALID",
                                    String.format("只有自制和委外类型的物料可以创建BOM，物料 %s 的属性为: %s",
                                            header.materialCode,
                                            parentMaterial.getErpClsId() != null ? parentMaterial.getErpClsId() : "未设置")));
                        }
                        continue;
                    }

                    // 检查BOM是否已存在
                    Optional<BillOfMaterial> existingBom = bomRepository.findByMaterialIdAndVersion(
                            parentMaterial.getId(), header.version);

                    BillOfMaterial bom;
                    if (existingBom.isPresent()) {
                        bom = existingBom.get();
                        // 删除现有明细项（如果需要更新）
                        bomItemRepository.deleteByBomId(bom.getId());
                        logger.debug("更新已存在的BOM: 物料={}, 版本={}", header.materialCode, header.version);
                    } else {
                        // 创建新的BOM
                        bom = BillOfMaterial.builder()
                                .material(parentMaterial)
                                .version(header.version)
                                .name(header.name)
                                .category(header.category)
                                .usage(header.usage)
                                .description(header.description)
                                .build();
                        bom = bomRepository.save(bom);
                    }

                    bomSuccessCount.incrementAndGet();

                    // 导入明细项
                    int sequence = 1;
                    for (BomItemData itemData : items) {
                        itemTotalCount.incrementAndGet();

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
                                // 使用子项物料的基础单位
                                childUnit = childMaterial.getBaseUnit();
                            }

                            Integer seq = itemData.sequence != null ? itemData.sequence : sequence++;

                            BigDecimal numerator = BigDecimal.ONE;
                            if (itemData.numerator != null && !itemData.numerator.trim().isEmpty()) {
                                try {
                                    numerator = new BigDecimal(itemData.numerator.trim());
                                } catch (NumberFormatException e) {
                                    logger.warn("用量分子格式错误: {}, 使用默认值1", itemData.numerator);
                                }
                            }

                            BigDecimal denominator = BigDecimal.ONE;
                            if (itemData.denominator != null && !itemData.denominator.trim().isEmpty()) {
                                try {
                                    denominator = new BigDecimal(itemData.denominator.trim());
                                } catch (NumberFormatException e) {
                                    logger.warn("用量分母格式错误: {}, 使用默认值1", itemData.denominator);
                                }
                            }

                            BigDecimal scrapRate = null;
                            if (itemData.scrapRate != null && !itemData.scrapRate.trim().isEmpty()) {
                                try {
                                    scrapRate = new BigDecimal(itemData.scrapRate.trim());
                                } catch (NumberFormatException e) {
                                    logger.warn("损耗率格式错误: {}", itemData.scrapRate);
                                }
                            }

                            // 处理子项BOM版本：如果导入文件中未配置，保持为null，不设置默认值
                            String childBomVersion = (itemData.childBomVersion != null && !itemData.childBomVersion.trim().isEmpty())
                                    ? itemData.childBomVersion.trim() : null;

                            // 只有在明确配置了子项BOM版本时，才验证子项物料属性
                            if (childBomVersion != null) {
                                if (!isValidBomMaterialType(childMaterial.getErpClsId())) {
                                    if (itemErrors.size() < MAX_ERROR_COUNT) {
                                        itemErrors.add(new BomImportResponse.ImportError(
                                                "BOM明细", itemData.rowNumber, "FBOMID",
                                                String.format("只有自制和委外类型的物料可以设置子项BOM版本，物料 %s 的属性为: %s",
                                                        itemData.childMaterialCode,
                                                        childMaterial.getErpClsId() != null ? childMaterial.getErpClsId() : "未设置")));
                                    }
                                    continue;
                                }
                            }

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

                            bomItemRepository.save(bomItem);
                            itemSuccessCount.incrementAndGet();
                        } catch (Exception e) {
                            logger.error("导入BOM明细失败: 行{}", itemData.rowNumber, e);
                            if (itemErrors.size() < MAX_ERROR_COUNT) {
                                itemErrors.add(new BomImportResponse.ImportError(
                                        "BOM明细", itemData.rowNumber, null,
                                        "导入失败: " + e.getMessage()));
                            }
                        }
                    }
                } catch (Exception e) {
                    logger.error("导入BOM失败: 物料={}, 版本={}", header.materialCode, header.version, e);
                    if (bomErrors.size() < MAX_ERROR_COUNT) {
                        bomErrors.add(new BomImportResponse.ImportError(
                                "BOM", header.rowNumber, null,
                                "导入失败: " + e.getMessage()));
                    }
                }
            }

            return new BomImportResponse(
                    new BomImportResponse.BomImportResult(
                            bomGroups.size(),
                            bomSuccessCount.get(),
                            bomGroups.size() - bomSuccessCount.get(),
                            bomErrors),
                    new BomImportResponse.BomItemImportResult(
                            itemTotalCount.get(),
                            itemSuccessCount.get(),
                            itemTotalCount.get() - itemSuccessCount.get(),
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
         * 判断物料属性是否为有效的BOM物料类型（自制或委外）
         * 只支持中文名称："自制"、"委外"
         */
        private boolean isValidBomMaterialType(String erpClsId) {
            if (erpClsId == null) {
                return false;
            }
            String trimmed = erpClsId.trim();
            return trimmed.equals("自制") || trimmed.equals("委外");
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

