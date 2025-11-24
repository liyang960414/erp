package com.sambound.erp.service.importing.subreq;

import cn.idev.excel.FastExcel;
import cn.idev.excel.context.AnalysisContext;
import cn.idev.excel.read.listener.ReadListener;
import com.sambound.erp.dto.SubReqOrderImportResponse;
import com.sambound.erp.entity.BillOfMaterial;
import com.sambound.erp.entity.Material;
import com.sambound.erp.entity.SubReqOrder;
import com.sambound.erp.entity.SubReqOrderItem;
import com.sambound.erp.entity.Supplier;
import com.sambound.erp.entity.Unit;
import com.sambound.erp.repository.BillOfMaterialRepository;
import com.sambound.erp.repository.MaterialRepository;
import com.sambound.erp.repository.SubReqOrderItemRepository;
import com.sambound.erp.repository.SubReqOrderRepository;
import com.sambound.erp.repository.SupplierRepository;
import com.sambound.erp.repository.UnitRepository;
import com.sambound.erp.service.importing.ImportError;
import com.sambound.erp.service.importing.dto.SubReqOrderExcelRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionTemplate;

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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 委外订单 Excel 导入处理器。
 */
public class SubReqOrderImportProcessor implements ReadListener<SubReqOrderExcelRow> {

    private static final Logger logger = LoggerFactory.getLogger(SubReqOrderImportProcessor.class);
    private static final int MAX_ERROR_COUNT = 1000;
    private static final int BATCH_SIZE = 100;
    private static final int MAX_CONCURRENT_BATCHES = 10;

    private final SubReqOrderRepository subReqOrderRepository;
    private final SubReqOrderItemRepository subReqOrderItemRepository;
    private final SupplierRepository supplierRepository;
    private final MaterialRepository materialRepository;
    private final UnitRepository unitRepository;
    private final BillOfMaterialRepository bomRepository;
    private final TransactionTemplate transactionTemplate;
    private final ExecutorService executorService;

    private final List<SubReqOrderData> orderDataList = new ArrayList<>();
    private final AtomicInteger totalRows = new AtomicInteger(0);
    private SubReqOrderHeader currentHeader;

    public SubReqOrderImportProcessor(SubReqOrderRepository subReqOrderRepository,
                                     SubReqOrderItemRepository subReqOrderItemRepository,
                                     SupplierRepository supplierRepository,
                                     MaterialRepository materialRepository,
                                     UnitRepository unitRepository,
                                     BillOfMaterialRepository bomRepository,
                                     TransactionTemplate transactionTemplate,
                                     ExecutorService executorService) {
        this.subReqOrderRepository = subReqOrderRepository;
        this.subReqOrderItemRepository = subReqOrderItemRepository;
        this.supplierRepository = supplierRepository;
        this.materialRepository = materialRepository;
        this.unitRepository = unitRepository;
        this.bomRepository = bomRepository;
        this.transactionTemplate = transactionTemplate;
        this.executorService = executorService;
    }

    public SubReqOrderImportResponse process(InputStream inputStream) {
        orderDataList.clear();
        totalRows.set(0);
        currentHeader = null;

        FastExcel.read(inputStream, SubReqOrderExcelRow.class, this)
                .sheet("委外订单#单据头(FBillHead)")
                .headRowNumber(2)
                .doRead();

        return importToDatabase();
    }

    @Override
    public void invoke(SubReqOrderExcelRow data, AnalysisContext context) {
        totalRows.incrementAndGet();
        int rowNum = context.readRowHolder().getRowIndex();

        String billHead = trimOrNull(data.getBillHead());
        if (billHead != null) {
            Integer billHeadSeq = null;
            try {
                billHeadSeq = Integer.parseInt(billHead);
            } catch (NumberFormatException e) {
                // ignore
            }
            if (billHeadSeq != null) {
                currentHeader = new SubReqOrderHeader(
                        rowNum,
                        billHeadSeq,
                        data.getHeaderDescription()
                );
            }
        }

        String treeEntity = trimOrNull(data.getTreeEntity());
        if (treeEntity != null && currentHeader != null) {
            Integer sequence = null;
            try {
                sequence = Integer.parseInt(treeEntity);
            } catch (NumberFormatException e) {
                // ignore
            }
            if (sequence != null) {
                SubReqOrderItemHeader itemHeader = new SubReqOrderItemHeader(
                        rowNum,
                        sequence,
                        data.getMaterialCode(),
                        data.getUnitCode(),
                        data.getQty(),
                        data.getBomVersion(),
                        data.getSupplierCode(),
                        data.getLotMaster(),
                        data.getLotManual(),
                        data.getBaseNoStockInQty(),
                        data.getNoStockInQty(),
                        data.getPickMtrlStatus(),
                        data.getDescription()
                );
                orderDataList.add(new SubReqOrderData(currentHeader, itemHeader));
            }
        }
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext context) {
        logger.info("委外订单数据收集完成，共 {} 条订单明细数据", orderDataList.size());
    }

    private SubReqOrderImportResponse importToDatabase() {
        if (orderDataList.isEmpty()) {
            logger.info("未找到委外订单数据");
            return new SubReqOrderImportResponse(
                    new SubReqOrderImportResponse.SubReqOrderImportResult(0, 0, 0, new ArrayList<>())
            );
        }

        long startTime = System.currentTimeMillis();
        int estimatedOrderCount = Math.max(1000, orderDataList.size() / 10);
        Map<Integer, SubReqOrderHeader> headerMap = new LinkedHashMap<>(estimatedOrderCount);
        Map<Integer, Map<Integer, SubReqOrderItemHeader>> itemHeaderMap = new LinkedHashMap<>(estimatedOrderCount);

        for (SubReqOrderData data : orderDataList) {
            if (data == null || data.header() == null || data.itemHeader() == null) {
                continue;
            }
            Integer billHeadSeq = data.header().billHeadSeq();
            if (billHeadSeq == null) {
                continue;
            }
            headerMap.putIfAbsent(billHeadSeq, data.header());
            Map<Integer, SubReqOrderItemHeader> items = itemHeaderMap.computeIfAbsent(billHeadSeq, key -> new LinkedHashMap<>());
            Integer sequence = data.itemHeader().sequence();
            SubReqOrderItemHeader itemHeader = data.itemHeader();
            if (sequence == null) {
                sequence = items.size() + 1;
                itemHeader = new SubReqOrderItemHeader(
                        data.itemHeader().rowNumber(),
                        sequence,
                        data.itemHeader().materialCode(),
                        data.itemHeader().unitCode(),
                        data.itemHeader().qty(),
                        data.itemHeader().bomVersion(),
                        data.itemHeader().supplierCode(),
                        data.itemHeader().lotMaster(),
                        data.itemHeader().lotManual(),
                        data.itemHeader().baseNoStockInQty(),
                        data.itemHeader().noStockInQty(),
                        data.itemHeader().pickMtrlStatus(),
                        data.itemHeader().description()
                );
            }
            items.putIfAbsent(sequence, itemHeader);
        }

        int totalOrderCount = headerMap.size();
        int totalItemCount = itemHeaderMap.values().stream().mapToInt(Map::size).sum();
        logger.info("找到 {} 个订单，{} 条明细，开始导入到数据库", totalOrderCount, totalItemCount);

        if (totalOrderCount == 0) {
            logger.info("未收集到有效的委外订单");
            return new SubReqOrderImportResponse(
                    new SubReqOrderImportResponse.SubReqOrderImportResult(0, 0, 0, new ArrayList<>())
            );
        }

        List<ImportError> errors = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger successCount = new AtomicInteger(0);

        Map<String, Supplier> supplierCache = preloadSuppliers(itemHeaderMap, errors);
        Map<String, Material> materialCache = new HashMap<>();
        Map<String, Unit> unitCache = new HashMap<>();
        Map<String, BillOfMaterial> bomCache = new HashMap<>();
        preloadMaterialsUnitsAndBoms(itemHeaderMap, materialCache, unitCache, bomCache);

        Map<Integer, SubReqOrder> existingOrderMap = preloadExistingOrders(headerMap.keySet());

        List<Map.Entry<Integer, Map<Integer, SubReqOrderItemHeader>>> orderList =
                new ArrayList<>(itemHeaderMap.entrySet());

        List<CompletableFuture<BatchResult>> futures = new ArrayList<>();
        Semaphore batchSemaphore = new Semaphore(MAX_CONCURRENT_BATCHES);
        int totalBatches = (orderList.size() + BATCH_SIZE - 1) / BATCH_SIZE;

        for (int i = 0; i < orderList.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, orderList.size());
            List<Map.Entry<Integer, Map<Integer, SubReqOrderItemHeader>>> batch =
                    new ArrayList<>(orderList.subList(i, end));
            int batchIndex = (i / BATCH_SIZE) + 1;

            CompletableFuture<BatchResult> future = CompletableFuture.supplyAsync(() -> {
                try {
                    batchSemaphore.acquire();
                    try {
                        long batchStartTime = System.currentTimeMillis();
                        logger.info("处理委外订单批次 {}/{}，订单数量: {}", batchIndex, totalBatches, batch.size());

                        int batchSuccess = transactionTemplate.execute(status ->
                                importBatchOrders(batch, headerMap,
                                        supplierCache, materialCache, unitCache, bomCache,
                                        existingOrderMap, errors)
                        );

                        long batchDuration = System.currentTimeMillis() - batchStartTime;
                        logger.info("批次 {}/{} 完成，耗时: {}ms，成功: {} 条",
                                batchIndex, totalBatches, batchDuration, batchSuccess);

                        return new BatchResult(batchSuccess, List.of());
                    } finally {
                        batchSemaphore.release();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.warn("批次 {} 处理被中断", batchIndex);
                    return new BatchResult(0, buildBatchErrors(batch, headerMap, "批次处理被中断"));
                } catch (Exception e) {
                    logger.error("批次 {} 导入失败", batchIndex, e);
                    return new BatchResult(0, buildBatchErrors(batch, headerMap, "批次导入失败: " + e.getMessage()));
                }
            }, executorService);

            futures.add(future);
        }

        waitForBatches(futures, successCount, errors);

        long totalDuration = System.currentTimeMillis() - startTime;
        logger.info("委外订单导入完成：总耗时 {}ms，总计 {} 条，成功 {} 条，失败 {} 条",
                totalDuration, totalOrderCount, successCount.get(), totalOrderCount - successCount.get());

        return new SubReqOrderImportResponse(
                new SubReqOrderImportResponse.SubReqOrderImportResult(
                        totalOrderCount,
                        successCount.get(),
                        totalOrderCount - successCount.get(),
                        new ArrayList<>(errors))
        );
    }

    private void waitForBatches(List<CompletableFuture<BatchResult>> futures,
                                AtomicInteger successCount,
                                List<ImportError> errors) {
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(30, TimeUnit.MINUTES);

            for (CompletableFuture<BatchResult> future : futures) {
                try {
                    BatchResult result = future.get();
                    successCount.addAndGet(result.successCount());
                    if (!result.errors().isEmpty()) {
                        synchronized (errors) {
                            errors.addAll(result.errors());
                        }
                    }
                } catch (Exception e) {
                    logger.error("获取批次结果失败", e);
                }
            }
        } catch (TimeoutException e) {
            logger.error("导入超时", e);
            futures.forEach(f -> f.cancel(true));
            throw new RuntimeException("导入超时，请检查数据量或重试", e);
        } catch (Exception e) {
            logger.error("批次处理失败", e);
            futures.forEach(f -> f.cancel(true));
            throw new RuntimeException("批次处理失败: " + e.getMessage(), e);
        }
    }

    private int importBatchOrders(
            List<Map.Entry<Integer, Map<Integer, SubReqOrderItemHeader>>> batch,
            Map<Integer, SubReqOrderHeader> headerMap,
            Map<String, Supplier> supplierCache,
            Map<String, Material> materialCache,
            Map<String, Unit> unitCache,
            Map<String, BillOfMaterial> bomCache,
            Map<Integer, SubReqOrder> existingOrderMap,
            List<ImportError> errors) {

        List<SubReqOrder> ordersToSave = new ArrayList<>();
        List<OrderItemData> itemsToSave = new ArrayList<>();
        Map<Integer, SubReqOrder> orderMap = new LinkedHashMap<>();
        Map<Integer, Map<Integer, SubReqOrderItem>> itemMap = new LinkedHashMap<>();

        for (Map.Entry<Integer, Map<Integer, SubReqOrderItemHeader>> entry : batch) {
            Integer billHeadSeq = entry.getKey();
            SubReqOrderHeader header = headerMap.get(billHeadSeq);
            Map<Integer, SubReqOrderItemHeader> items = entry.getValue();

            try {
                if (items == null || items.isEmpty()) {
                    addError(errors, "委外订单", header.rowNumber(), "订单明细",
                            "委外订单必须至少有一个订单明细，单据头序号: " + billHeadSeq);
                    logger.warn("订单 {} 没有订单明细，跳过导入", billHeadSeq);
                    continue;
                }

                SubReqOrder subReqOrder = existingOrderMap.get(billHeadSeq);
                boolean isNewOrder = (subReqOrder == null);

                if (isNewOrder) {
                    subReqOrder = SubReqOrder.builder()
                            .billHeadSeq(header.billHeadSeq())
                            .description(header.description())
                            .status(SubReqOrder.OrderStatus.OPEN)
                            .build();
                    ordersToSave.add(subReqOrder);
                }

                orderMap.put(billHeadSeq, subReqOrder);
                itemMap.put(billHeadSeq, new LinkedHashMap<>());

                for (Map.Entry<Integer, SubReqOrderItemHeader> itemEntry : items.entrySet()) {
                    Integer sequence = itemEntry.getKey();
                    SubReqOrderItemHeader itemHeader = itemEntry.getValue();

                    try {
                        Material material = resolveMaterial(itemHeader, materialCache, errors);
                        if (material == null) {
                            continue;
                        }

                        Unit unit = resolveUnit(itemHeader, unitCache, errors);
                        if (unit == null) {
                            continue;
                        }

                        Supplier supplier = resolveSupplier(itemHeader, supplierCache, errors);
                        if (supplier == null) {
                            continue;
                        }

                        BigDecimal qty = parseRequiredDecimal(
                                itemHeader.qty(), itemHeader.rowNumber(), "数量", errors);
                        if (qty == null) {
                            continue;
                        }

                        BillOfMaterial bom = resolveBom(itemHeader, materialCache, bomCache);
                        BigDecimal baseNoStockInQty = parseOptionalDecimal(itemHeader.baseNoStockInQty());
                        BigDecimal noStockInQty = parseOptionalDecimal(itemHeader.noStockInQty());

                        SubReqOrderItem item = SubReqOrderItem.builder()
                                .subReqOrder(subReqOrder)
                                .sequence(sequence)
                                .material(material)
                                .unit(unit)
                                .qty(qty)
                                .bom(bom)
                                .supplier(supplier)
                                .lotMaster(itemHeader.lotMaster())
                                .lotManual(itemHeader.lotManual())
                                .baseNoStockInQty(baseNoStockInQty)
                                .noStockInQty(noStockInQty)
                                .pickMtrlStatus(itemHeader.pickMtrlStatus())
                                .description(itemHeader.description())
                                .build();

                        itemsToSave.add(new OrderItemData(billHeadSeq, sequence, item, itemHeader.rowNumber()));
                        itemMap.get(billHeadSeq).put(sequence, item);
                    } catch (Exception e) {
                        logger.error("处理订单明细失败，行号: {}", itemHeader.rowNumber(), e);
                        addError(errors, "委外订单明细", itemHeader.rowNumber(), null,
                                "处理失败: " + e.getMessage());
                    }
                }
            } catch (Exception e) {
                logger.error("处理订单失败，单据头序号: {}", header.billHeadSeq(), e);
                addError(errors, "委外订单", header.rowNumber(), "单据头序号",
                        "处理失败: " + e.getMessage());
            }
        }

        int successCount = 0;

        try {
            if (!ordersToSave.isEmpty()) {
                logger.debug("批量保存 {} 个委外订单", ordersToSave.size());
                List<SubReqOrder> savedOrders = subReqOrderRepository.saveAll(ordersToSave);
                for (SubReqOrder savedOrder : savedOrders) {
                    orderMap.put(savedOrder.getBillHeadSeq(), savedOrder);
                }
            }

            if (!itemsToSave.isEmpty()) {
                logger.debug("批量保存 {} 条订单明细", itemsToSave.size());
                for (OrderItemData itemData : itemsToSave) {
                    SubReqOrder order = orderMap.get(itemData.billHeadSeq());
                    if (order != null) {
                        itemData.item().setSubReqOrder(order);
                    }
                }
                List<SubReqOrderItem> savedItems = subReqOrderItemRepository.saveAll(
                        itemsToSave.stream().map(OrderItemData::item).toList());
                for (int i = 0; i < savedItems.size() && i < itemsToSave.size(); i++) {
                    SubReqOrderItem savedItem = savedItems.get(i);
                    OrderItemData itemData = itemsToSave.get(i);
                    itemMap.get(itemData.billHeadSeq()).put(itemData.sequence(), savedItem);
                }
            }

            Set<Integer> ordersWithItems = new HashSet<>();
            for (OrderItemData itemData : itemsToSave) {
                ordersWithItems.add(itemData.billHeadSeq());
            }
            successCount = ordersWithItems.size();
            logger.debug("批次处理完成，成功保存 {} 个订单（共 {} 条订单明细）",
                    successCount, itemsToSave.size());

        } catch (Exception e) {
            logger.error("批量保存失败", e);
            for (Integer billHeadSeq : orderMap.keySet()) {
                SubReqOrderHeader header = headerMap.get(billHeadSeq);
                if (header != null) {
                    addError(errors, "委外订单", header.rowNumber(), "单据头序号",
                            "批量保存失败: " + e.getMessage());
                }
            }
        }

        return successCount;
    }

    private Supplier resolveSupplier(SubReqOrderItemHeader itemHeader,
                                     Map<String, Supplier> supplierCache,
                                     List<ImportError> errors) {
        String supplierCode = trimOrNull(itemHeader.supplierCode());
        if (supplierCode == null) {
            addError(errors, "委外订单明细", itemHeader.rowNumber(), "供应商编码", "供应商编码为空");
            return null;
        }
        Supplier supplier = supplierCache.get(supplierCode);
        if (supplier == null) {
            addError(errors, "委外订单明细", itemHeader.rowNumber(), "供应商编码",
                    "供应商不存在: " + supplierCode);
        }
        return supplier;
    }

    private Material resolveMaterial(SubReqOrderItemHeader itemHeader,
                                     Map<String, Material> materialCache,
                                     List<ImportError> errors) {
        String materialCode = trimOrNull(itemHeader.materialCode());
        if (materialCode == null) {
            addError(errors, "委外订单明细", itemHeader.rowNumber(), "物料编码", "物料编码为空");
            return null;
        }
        Material material = materialCache.get(materialCode);
        if (material == null) {
            addError(errors, "委外订单明细", itemHeader.rowNumber(), "物料编码",
                    "物料不存在: " + materialCode);
        }
        return material;
    }

    private Unit resolveUnit(SubReqOrderItemHeader itemHeader,
                             Map<String, Unit> unitCache,
                             List<ImportError> errors) {
        String unitCode = trimOrNull(itemHeader.unitCode());
        if (unitCode == null) {
            addError(errors, "委外订单明细", itemHeader.rowNumber(), "单位编码", "单位编码为空");
            return null;
        }
        Unit unit = unitCache.get(unitCode);
        if (unit == null) {
            addError(errors, "委外订单明细", itemHeader.rowNumber(), "单位编码",
                    "单位不存在: " + unitCode);
        }
        return unit;
    }

    private BillOfMaterial resolveBom(SubReqOrderItemHeader itemHeader,
                                      Map<String, Material> materialCache,
                                      Map<String, BillOfMaterial> bomCache) {
        String materialCode = trimOrNull(itemHeader.materialCode());
        String bomVersion = trimOrNull(itemHeader.bomVersion());
        if (materialCode == null || bomVersion == null) {
            return null;
        }
        return bomCache.get(materialCode + ":" + bomVersion);
    }

    private BigDecimal parseRequiredDecimal(String value,
                                            int rowNumber,
                                            String field,
                                            List<ImportError> errors) {
        if (value == null || value.trim().isEmpty()) {
            addError(errors, "委外订单明细", rowNumber, field, field + "为空");
            return null;
        }
        try {
            return new BigDecimal(value.trim().replace(",", ""));
        } catch (Exception e) {
            addError(errors, "委外订单明细", rowNumber, field,
                    "数量格式错误: " + value);
            return null;
        }
    }

    private BigDecimal parseOptionalDecimal(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(value.trim().replace(",", ""));
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, Supplier> preloadSuppliers(
            Map<Integer, Map<Integer, SubReqOrderItemHeader>> itemHeaderMap,
            List<ImportError> errors) {

        Set<String> supplierCodes = new HashSet<>();
        for (Map<Integer, SubReqOrderItemHeader> items : itemHeaderMap.values()) {
            for (SubReqOrderItemHeader item : items.values()) {
                String code = trimOrNull(item.supplierCode());
                if (code != null) {
                    supplierCodes.add(code);
                }
            }
        }

        if (supplierCodes.isEmpty()) {
            logger.info("未找到需要查询的供应商");
            return new HashMap<>();
        }

        logger.info("开始预加载 {} 个供应商", supplierCodes.size());
        long startTime = System.currentTimeMillis();

        Map<String, Supplier> supplierCache = new HashMap<>(supplierCodes.size());
        List<String> codesToQuery = new ArrayList<>(supplierCodes);
        for (int i = 0; i < codesToQuery.size(); i += 1000) {
            int end = Math.min(i + 1000, codesToQuery.size());
            List<String> chunk = codesToQuery.subList(i, end);
            List<Supplier> suppliers = supplierRepository.findByCodeIn(chunk);
            for (Supplier supplier : suppliers) {
                supplierCache.put(supplier.getCode(), supplier);
            }
        }

        for (String code : supplierCodes) {
            if (!supplierCache.containsKey(code)) {
                addError(errors, "供应商", 0, "供应商编码",
                        String.format("供应商不存在: %s", code));
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        logger.info("供应商预加载完成：共 {} 个，找到 {} 个，耗时 {}ms",
                supplierCodes.size(), supplierCache.size(), duration);
        return supplierCache;
    }

    private void preloadMaterialsUnitsAndBoms(
            Map<Integer, Map<Integer, SubReqOrderItemHeader>> itemHeaderMap,
            Map<String, Material> materialCache,
            Map<String, Unit> unitCache,
            Map<String, BillOfMaterial> bomCache) {

        int estimatedItemCount = itemHeaderMap.values().stream().mapToInt(Map::size).sum();
        Set<String> materialCodes = new HashSet<>(Math.max(100, estimatedItemCount / 2));
        Set<String> unitCodes = new HashSet<>(Math.max(50, estimatedItemCount / 10));
        Map<String, String> bomKeys = new HashMap<>(Math.max(100, estimatedItemCount / 10));

        for (Map<Integer, SubReqOrderItemHeader> items : itemHeaderMap.values()) {
            for (SubReqOrderItemHeader item : items.values()) {
                String materialCode = trimOrNull(item.materialCode());
                if (materialCode != null) {
                    materialCodes.add(materialCode);
                    String bomVersion = trimOrNull(item.bomVersion());
                    if (bomVersion != null) {
                        bomKeys.put(materialCode + ":" + bomVersion, materialCode + ":" + bomVersion);
                    }
                }
                String unitCode = trimOrNull(item.unitCode());
                if (unitCode != null) {
                    unitCodes.add(unitCode);
                }
            }
        }

        if (!materialCodes.isEmpty()) {
            List<String> materialCodeList = new ArrayList<>(materialCodes);
            for (int i = 0; i < materialCodeList.size(); i += 1000) {
                int end = Math.min(i + 1000, materialCodeList.size());
                List<String> chunk = materialCodeList.subList(i, end);
                // 使用 JOIN FETCH 预加载 MaterialGroup 和 baseUnit，避免 LazyInitializationException
                List<Material> materials = materialRepository.findByCodeInWithMaterialGroup(chunk);
                for (Material material : materials) {
                    materialCache.put(material.getCode(), material);
                    // 确保懒加载字段已初始化（在事务内）
                    if (material.getMaterialGroup() != null) {
                        material.getMaterialGroup().getId();
                    }
                    if (material.getBaseUnit() != null) {
                        material.getBaseUnit().getId();
                    }
                }
            }
        }

        if (!unitCodes.isEmpty()) {
            List<String> unitCodeList = new ArrayList<>(unitCodes);
            for (int i = 0; i < unitCodeList.size(); i += 1000) {
                int end = Math.min(i + 1000, unitCodeList.size());
                List<String> chunk = unitCodeList.subList(i, end);
                // 使用 JOIN FETCH 预加载 UnitGroup，避免 LazyInitializationException
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

        if (!bomKeys.isEmpty()) {
            for (Map.Entry<String, String> entry : bomKeys.entrySet()) {
                String[] parts = entry.getKey().split(":", 2);
                if (parts.length == 2) {
                    String materialCode = parts[0];
                    String version = parts[1];
                    Material material = materialCache.get(materialCode);
                    if (material != null) {
                        bomRepository.findByMaterialIdAndVersion(material.getId(), version)
                                .ifPresent(bom -> bomCache.put(entry.getKey(), bom));
                    }
                }
            }
        }
    }

    private Map<Integer, SubReqOrder> preloadExistingOrders(Set<Integer> billHeadSeqs) {
        Map<Integer, SubReqOrder> existingOrderMap = new HashMap<>();
        for (Integer billHeadSeq : billHeadSeqs) {
            subReqOrderRepository.findByBillHeadSeq(billHeadSeq).ifPresent(order -> existingOrderMap.put(billHeadSeq, order));
        }
        return existingOrderMap;
    }

    private List<ImportError> buildBatchErrors(
            List<Map.Entry<Integer, Map<Integer, SubReqOrderItemHeader>>> batch,
            Map<Integer, SubReqOrderHeader> headerMap,
            String message) {
        List<ImportError> batchErrors = new ArrayList<>();
        for (Map.Entry<Integer, Map<Integer, SubReqOrderItemHeader>> entry : batch) {
            SubReqOrderHeader header = headerMap.get(entry.getKey());
            if (header != null && batchErrors.size() < MAX_ERROR_COUNT) {
                batchErrors.add(new ImportError(
                        "委外订单", header.rowNumber(), "单据头序号", message));
            }
        }
        return batchErrors;
    }

    private void addError(List<ImportError> errors,
                          String section,
                          int rowNumber,
                          String field,
                          String message) {
        if (errors.size() < MAX_ERROR_COUNT) {
            errors.add(new ImportError(section, rowNumber, field, message));
        }
    }

    private String trimOrNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    // 内部数据类
    private record SubReqOrderData(SubReqOrderHeader header, SubReqOrderItemHeader itemHeader) {}
    
    private record SubReqOrderHeader(int rowNumber, Integer billHeadSeq, String description) {}
    
    private record SubReqOrderItemHeader(
            int rowNumber,
            Integer sequence,
            String materialCode,
            String unitCode,
            String qty,
            String bomVersion,
            String supplierCode,
            String lotMaster,
            String lotManual,
            String baseNoStockInQty,
            String noStockInQty,
            String pickMtrlStatus,
            String description
    ) {}
    
    private record OrderItemData(Integer billHeadSeq, Integer sequence, SubReqOrderItem item, int rowNumber) {}
    
    private record BatchResult(int successCount, List<ImportError> errors) {}
}

