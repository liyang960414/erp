package com.sambound.erp.service.importing.purchase;

import cn.idev.excel.FastExcel;
import cn.idev.excel.context.AnalysisContext;
import cn.idev.excel.read.listener.ReadListener;
import com.sambound.erp.config.ImportConfiguration;
import com.sambound.erp.dto.PurchaseOrderImportResponse;
import com.sambound.erp.entity.BillOfMaterial;
import com.sambound.erp.entity.Material;
import com.sambound.erp.entity.PurchaseOrder;
import com.sambound.erp.entity.PurchaseOrderItem;
import com.sambound.erp.entity.SubReqOrderItem;
import com.sambound.erp.entity.Supplier;
import com.sambound.erp.entity.Unit;
import com.sambound.erp.repository.BillOfMaterialRepository;
import com.sambound.erp.repository.MaterialRepository;
import com.sambound.erp.repository.PurchaseOrderItemRepository;
import com.sambound.erp.repository.PurchaseOrderRepository;
import com.sambound.erp.repository.SubReqOrderItemRepository;
import com.sambound.erp.repository.SupplierRepository;
import com.sambound.erp.repository.UnitRepository;
import com.sambound.erp.service.importing.ImportError;
import com.sambound.erp.service.importing.dto.PurchaseOrderExcelRow;
import com.sambound.erp.service.importing.exception.ImportProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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
 * 采购订单 Excel 导入处理器。
 */
public class PurchaseOrderImportProcessor implements ReadListener<PurchaseOrderExcelRow> {

    private static final Logger logger = LoggerFactory.getLogger(PurchaseOrderImportProcessor.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderItemRepository purchaseOrderItemRepository;
    private final SupplierRepository supplierRepository;
    private final MaterialRepository materialRepository;
    private final UnitRepository unitRepository;
    private final BillOfMaterialRepository bomRepository;
    private final SubReqOrderItemRepository subReqOrderItemRepository;
    private final TransactionTemplate transactionTemplate;
    private final ExecutorService executorService;
    private final ImportConfiguration importConfig;

    private final List<PurchaseOrderData> orderDataList = new ArrayList<>();
    private final AtomicInteger totalRows = new AtomicInteger(0);
    private PurchaseOrderHeader currentHeader;

    public PurchaseOrderImportProcessor(PurchaseOrderRepository purchaseOrderRepository,
                                        PurchaseOrderItemRepository purchaseOrderItemRepository,
                                        SupplierRepository supplierRepository,
                                        MaterialRepository materialRepository,
                                        UnitRepository unitRepository,
                                        BillOfMaterialRepository bomRepository,
                                        SubReqOrderItemRepository subReqOrderItemRepository,
                                        TransactionTemplate transactionTemplate,
                                        ExecutorService executorService,
                                        ImportConfiguration importConfig) {
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.purchaseOrderItemRepository = purchaseOrderItemRepository;
        this.supplierRepository = supplierRepository;
        this.materialRepository = materialRepository;
        this.unitRepository = unitRepository;
        this.bomRepository = bomRepository;
        this.subReqOrderItemRepository = subReqOrderItemRepository;
        this.transactionTemplate = transactionTemplate;
        this.executorService = executorService;
        this.importConfig = importConfig;
    }

    public PurchaseOrderImportResponse process(byte[] fileBytes, String fileName) {
        logger.info("开始处理采购订单导入: {}", fileName);
        orderDataList.clear();
        totalRows.set(0);
        currentHeader = null;

        try {
            FastExcel.read(new ByteArrayInputStream(fileBytes), PurchaseOrderExcelRow.class, this)
                    .sheet("采购订单#基本信息(FBillHead)")
                    .headRowNumber(2)
                    .doRead();

            return importToDatabase(fileName);
        } catch (Exception e) {
            logger.error("采购订单导入处理失败", e);
            throw new ImportProcessingException("采购订单导入处理失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void invoke(PurchaseOrderExcelRow data, AnalysisContext context) {
        totalRows.incrementAndGet();
        int rowNum = context.readRowHolder().getRowIndex();

        String billNo = trimOrNull(data.getBillNo());
        if (billNo != null) {
            currentHeader = new PurchaseOrderHeader(
                    rowNum,
                    billNo,
                    data.getOrderDate(),
                    null,
                    data.getSupplierCode(),
                    data.getSupplierName()
            );
        }

        String purchaseOrderEntry = trimOrNull(data.getPurchaseOrderEntry());
        if (purchaseOrderEntry != null && currentHeader != null) {
            Integer sequence = null;
            try {
                // 去除千分符（逗号）和其他可能的格式字符
                String cleaned = purchaseOrderEntry.replace(",", "").replace(" ", "").trim();
                sequence = Integer.parseInt(cleaned);
            } catch (NumberFormatException e) {
                logger.warn("无法解析采购订单明细序号：{}，行号: {}", purchaseOrderEntry, rowNum);
                // ignore
            }
            Integer subReqOrderSequence = null;
            String subReqOrderSequenceStr = trimOrNull(data.getSubReqOrderSequence());
            if (subReqOrderSequenceStr != null) {
                try {
                    // 去除千分符（逗号）和其他可能的格式字符
                    String cleaned = subReqOrderSequenceStr.replace(",", "").replace(" ", "").trim();
                    subReqOrderSequence = Integer.parseInt(cleaned);
                } catch (NumberFormatException e) {
                    logger.warn("无法解析委外订单分录内码：{}，行号: {}", subReqOrderSequenceStr, rowNum);
                    // ignore
                }
            }
            PurchaseOrderItemHeader itemHeader = new PurchaseOrderItemHeader(
                    rowNum,
                    sequence,
                    data.getMaterialCode(),
                    data.getBomVersion(),
                    data.getMaterialDesc(),
                    data.getUnitCode(),
                    data.getQty(),
                    data.getPlanConfirm(),
                    data.getSalUnitCode(),
                    data.getSalQty(),
                    data.getSalJoinQty(),
                    data.getBaseSalJoinQty(),
                    data.getRemarks(),
                    data.getSalBaseQty(),
                    subReqOrderSequence
            );
            orderDataList.add(new PurchaseOrderData(currentHeader, itemHeader));
        }
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext context) {
        logger.info("采购订单数据收集完成，共 {} 条订单明细数据", orderDataList.size());
    }

    private PurchaseOrderImportResponse importToDatabase(String fileName) {
        if (orderDataList.isEmpty()) {
            logger.info("未找到采购订单数据");
            return new PurchaseOrderImportResponse(
                    new PurchaseOrderImportResponse.PurchaseOrderImportResult(0, 0, 0, new ArrayList<>())
            );
        }

        long startTime = System.currentTimeMillis();
        int estimatedOrderCount = Math.max(1000, orderDataList.size() / 10);
        Map<String, PurchaseOrderHeader> headerMap = new LinkedHashMap<>(estimatedOrderCount);
        Map<String, Map<Integer, PurchaseOrderItemHeader>> itemHeaderMap = new LinkedHashMap<>(estimatedOrderCount);

        for (PurchaseOrderData data : orderDataList) {
            if (data == null || data.header() == null || data.itemHeader() == null) {
                continue;
            }
            String billNo = data.header().billNo();
            if (billNo == null) {
                continue;
            }
            headerMap.putIfAbsent(billNo, data.header());
            Map<Integer, PurchaseOrderItemHeader> items = itemHeaderMap.computeIfAbsent(billNo, key -> new LinkedHashMap<>());
            Integer sequence = data.itemHeader().sequence();
            PurchaseOrderItemHeader itemHeader = data.itemHeader();
            if (sequence == null) {
                sequence = items.size() + 1;
                itemHeader = new PurchaseOrderItemHeader(
                        data.itemHeader().rowNumber(),
                        sequence,
                        data.itemHeader().materialCode(),
                        data.itemHeader().bomVersion(),
                        data.itemHeader().materialDesc(),
                        data.itemHeader().unitCode(),
                        data.itemHeader().qty(),
                        data.itemHeader().planConfirm(),
                        data.itemHeader().salUnitCode(),
                        data.itemHeader().salQty(),
                        data.itemHeader().salJoinQty(),
                        data.itemHeader().baseSalJoinQty(),
                        data.itemHeader().remarks(),
                        data.itemHeader().salBaseQty(),
                        data.itemHeader().subReqOrderSequence()
                );
            }
            items.putIfAbsent(sequence, itemHeader);
        }

        int totalOrderCount = headerMap.size();
        int totalItemCount = itemHeaderMap.values().stream().mapToInt(Map::size).sum();
        logger.info("找到 {} 个订单，{} 条明细，开始导入到数据库", totalOrderCount, totalItemCount);

        if (totalOrderCount == 0) {
            logger.info("未收集到有效的采购订单");
            return new PurchaseOrderImportResponse(
                    new PurchaseOrderImportResponse.PurchaseOrderImportResult(0, 0, 0, new ArrayList<>())
            );
        }

        List<ImportError> errors = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger successCount = new AtomicInteger(0);

        Map<String, Supplier> supplierCache = preloadSuppliers(headerMap, errors);
        Map<String, Material> materialCache = new HashMap<>();
        Map<String, Unit> unitCache = new HashMap<>();
        Map<String, BillOfMaterial> bomCache = new HashMap<>();
        preloadMaterialsUnitsAndBoms(itemHeaderMap, materialCache, unitCache, bomCache);

        Map<String, PurchaseOrder> existingOrderMap = preloadExistingOrders(headerMap.keySet());

        List<Map.Entry<String, Map<Integer, PurchaseOrderItemHeader>>> orderList =
                new ArrayList<>(itemHeaderMap.entrySet());

        List<CompletableFuture<BatchResult>> futures = new ArrayList<>();
        int maxConcurrentBatches = importConfig.getConcurrency().getMaxConcurrentBatches();
        Semaphore batchSemaphore = new Semaphore(maxConcurrentBatches);
        int batchSize = importConfig.getBatch().getInsertSize();
        int totalBatches = (orderList.size() + batchSize - 1) / batchSize;

        for (int i = 0; i < orderList.size(); i += batchSize) {
            int end = Math.min(i + batchSize, orderList.size());
            List<Map.Entry<String, Map<Integer, PurchaseOrderItemHeader>>> batch =
                    new ArrayList<>(orderList.subList(i, end));
            int batchIndex = (i / batchSize) + 1;

            CompletableFuture<BatchResult> future = CompletableFuture.supplyAsync(() -> {
                try {
                    batchSemaphore.acquire();
                    try {
                        long batchStartTime = System.currentTimeMillis();
                        logger.info("处理采购订单批次 {}/{}，订单数量: {}", batchIndex, totalBatches, batch.size());

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
        logger.info("采购订单导入完成 [{}]: 总耗时 {}ms，总计 {} 条，成功 {} 条，失败 {} 条",
                fileName, totalDuration, totalOrderCount, successCount.get(), totalOrderCount - successCount.get());

        return new PurchaseOrderImportResponse(
                new PurchaseOrderImportResponse.PurchaseOrderImportResult(
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
            int timeoutMinutes = importConfig.getTimeout().getProcessingTimeoutMinutes();
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(timeoutMinutes, TimeUnit.MINUTES);

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
            List<Map.Entry<String, Map<Integer, PurchaseOrderItemHeader>>> batch,
            Map<String, PurchaseOrderHeader> headerMap,
            Map<String, Supplier> supplierCache,
            Map<String, Material> materialCache,
            Map<String, Unit> unitCache,
            Map<String, BillOfMaterial> bomCache,
            Map<String, PurchaseOrder> existingOrderMap,
            List<ImportError> errors) {

        List<PurchaseOrder> ordersToSave = new ArrayList<>();
        List<OrderItemData> itemsToSave = new ArrayList<>();
        Map<String, PurchaseOrder> orderMap = new LinkedHashMap<>();
        Map<String, Map<Integer, PurchaseOrderItem>> itemMap = new LinkedHashMap<>();

        for (Map.Entry<String, Map<Integer, PurchaseOrderItemHeader>> entry : batch) {
            String billNo = entry.getKey();
            PurchaseOrderHeader header = headerMap.get(billNo);
            Map<Integer, PurchaseOrderItemHeader> items = entry.getValue();

            try {
                if (items == null || items.isEmpty()) {
                    addError(errors, "采购订单", header.rowNumber(), "订单明细",
                            "采购订单必须至少有一个订单明细，订单编号: " + billNo, null);
                    logger.warn("订单 {} 没有订单明细，跳过导入", billNo);
                    continue;
                }

                PurchaseOrder purchaseOrder = existingOrderMap.get(billNo);
                boolean isNewOrder = (purchaseOrder == null);

                if (isNewOrder) {
                    Supplier supplier = resolveSupplier(header, supplierCache, errors);
                    if (supplier == null) {
                        continue;
                    }

                    LocalDate orderDate = parseDate(header.orderDate(), header.rowNumber(), errors);
                    if (orderDate == null) {
                        continue;
                    }

                    purchaseOrder = PurchaseOrder.builder()
                            .billNo(header.billNo())
                            .orderDate(orderDate)
                            .supplier(supplier)
                            .note(header.note())
                            .status(PurchaseOrder.OrderStatus.OPEN)
                            .build();
                    ordersToSave.add(purchaseOrder);
                }

                orderMap.put(billNo, purchaseOrder);
                itemMap.put(billNo, new LinkedHashMap<>());

                for (Map.Entry<Integer, PurchaseOrderItemHeader> itemEntry : items.entrySet()) {
                    Integer sequence = itemEntry.getKey();
                    PurchaseOrderItemHeader itemHeader = itemEntry.getValue();

                    try {
                        Material material = resolveMaterial(itemHeader, materialCache, errors);
                        if (material == null) {
                            continue;
                        }

                        Unit unit = resolveUnit(itemHeader, unitCache, errors);
                        if (unit == null) {
                            continue;
                        }

                        BigDecimal qty = parseRequiredDecimal(
                                itemHeader.qty(), itemHeader.rowNumber(), "采购数量", errors);
                        if (qty == null) {
                            continue;
                        }

                        BillOfMaterial bom = resolveBom(itemHeader, bomCache);
                        Boolean planConfirm = parsePlanConfirm(itemHeader.planConfirm());
                        Unit salUnit = resolveSalUnit(itemHeader, unitCache);
                        BigDecimal salQty = parseOptionalDecimal(itemHeader.salQty());
                        BigDecimal salJoinQty = parseOptionalDecimal(itemHeader.salJoinQty());
                        BigDecimal baseSalJoinQty = parseOptionalDecimal(itemHeader.baseSalJoinQty());
                        BigDecimal salBaseQty = parseOptionalDecimal(itemHeader.salBaseQty());

                        // 查找并关联委外订单明细
                        SubReqOrderItem subReqOrderItem = null;
                        if (itemHeader.subReqOrderSequence() != null) {
                            logger.debug("尝试关联委外订单明细：物料编码={}, sequence={}, 行号={}", 
                                    material.getCode(), itemHeader.subReqOrderSequence(), itemHeader.rowNumber());
                            subReqOrderItem = resolveSubReqOrderItem(material, itemHeader.subReqOrderSequence(), itemHeader.rowNumber(), errors);
                            if (subReqOrderItem != null) {
                                logger.debug("成功关联委外订单明细：ID={}", subReqOrderItem.getId());
                            } else {
                                logger.debug("未能关联委外订单明细：物料编码={}, sequence={}", 
                                        material.getCode(), itemHeader.subReqOrderSequence());
                            }
                        } else {
                            logger.debug("采购订单明细行号={}未提供subReqOrderSequence，跳过关联", itemHeader.rowNumber());
                        }

                        PurchaseOrderItem item = PurchaseOrderItem.builder()
                                .purchaseOrder(purchaseOrder)
                                .sequence(sequence)
                                .material(material)
                                .bom(bom)
                                .materialDesc(itemHeader.materialDesc())
                                .unit(unit)
                                .qty(qty)
                                .planConfirm(planConfirm)
                                .salUnit(salUnit)
                                .salQty(salQty)
                                .salJoinQty(salJoinQty)
                                .baseSalJoinQty(baseSalJoinQty)
                                .remarks(itemHeader.remarks())
                                .salBaseQty(salBaseQty)
                                .subReqOrderItem(subReqOrderItem)
                                .build();

                        itemsToSave.add(new OrderItemData(billNo, sequence, item, itemHeader.rowNumber()));
                        itemMap.get(billNo).put(sequence, item);
                    } catch (Exception e) {
                        logger.error("处理订单明细失败，行号: {}", itemHeader.rowNumber(), e);
                        addError(errors, "采购订单明细", itemHeader.rowNumber(), null,
                                "处理失败: " + e.getMessage(), null);
                    }
                }
            } catch (Exception e) {
                logger.error("处理订单失败，单据编号: {}", header.billNo(), e);
                addError(errors, "采购订单", header.rowNumber(), "单据编号",
                        "处理失败: " + e.getMessage(), header.billNo());
            }
        }

        int successCount = 0;

        try {
            if (!ordersToSave.isEmpty()) {
                logger.debug("批量保存 {} 个采购订单", ordersToSave.size());
                List<PurchaseOrder> savedOrders = purchaseOrderRepository.saveAll(ordersToSave);
                for (PurchaseOrder savedOrder : savedOrders) {
                    orderMap.put(savedOrder.getBillNo(), savedOrder);
                }
            }

            if (!itemsToSave.isEmpty()) {
                logger.debug("批量保存 {} 条订单明细", itemsToSave.size());
                for (OrderItemData itemData : itemsToSave) {
                    PurchaseOrder order = orderMap.get(itemData.billNo());
                    if (order != null) {
                        itemData.item().setPurchaseOrder(order);
                    }
                }
                List<PurchaseOrderItem> savedItems = purchaseOrderItemRepository.saveAll(
                        itemsToSave.stream().map(OrderItemData::item).toList());
                for (int i = 0; i < savedItems.size() && i < itemsToSave.size(); i++) {
                    PurchaseOrderItem savedItem = savedItems.get(i);
                    OrderItemData itemData = itemsToSave.get(i);
                    itemMap.get(itemData.billNo()).put(itemData.sequence(), savedItem);
                }
            }

            Set<String> ordersWithItems = new HashSet<>();
            for (OrderItemData itemData : itemsToSave) {
                ordersWithItems.add(itemData.billNo());
            }
            successCount = ordersWithItems.size();
            logger.debug("批次处理完成，成功保存 {} 个订单（共 {} 条订单明细）",
                    successCount, itemsToSave.size());

        } catch (Exception e) {
            logger.error("批量保存失败", e);
            for (String billNo : orderMap.keySet()) {
                PurchaseOrderHeader header = headerMap.get(billNo);
                if (header != null) {
                    addError(errors, "采购订单", header.rowNumber(), "单据编号",
                            "批量保存失败: " + e.getMessage(), billNo);
                }
            }
        }

        return successCount;
    }

    private Supplier resolveSupplier(PurchaseOrderHeader header,
                                     Map<String, Supplier> supplierCache,
                                     List<ImportError> errors) {
        String supplierCode = trimOrNull(header.supplierCode());
        if (supplierCode == null) {
            addError(errors, "采购订单", header.rowNumber(), "供应商编码", "供应商编码为空", null);
            return null;
        }
        Supplier supplier = supplierCache.get(supplierCode);
        if (supplier == null) {
            addError(errors, "采购订单", header.rowNumber(), "供应商编码",
                    "供应商不存在: " + supplierCode, supplierCode);
        }
        return supplier;
    }

    private Material resolveMaterial(PurchaseOrderItemHeader itemHeader,
                                     Map<String, Material> materialCache,
                                     List<ImportError> errors) {
        String materialCode = trimOrNull(itemHeader.materialCode());
        if (materialCode == null) {
            addError(errors, "采购订单明细", itemHeader.rowNumber(), "物料编码", "物料编码为空", null);
            return null;
        }
        Material material = materialCache.get(materialCode);
        if (material == null) {
            addError(errors, "采购订单明细", itemHeader.rowNumber(), "物料编码",
                    "物料不存在: " + materialCode, materialCode);
        }
        return material;
    }

    private Unit resolveUnit(PurchaseOrderItemHeader itemHeader,
                             Map<String, Unit> unitCache,
                             List<ImportError> errors) {
        String unitCode = trimOrNull(itemHeader.unitCode());
        if (unitCode == null) {
            addError(errors, "采购订单明细", itemHeader.rowNumber(), "单位编码", "单位编码为空", null);
            return null;
        }
        Unit unit = unitCache.get(unitCode);
        if (unit == null) {
            addError(errors, "采购订单明细", itemHeader.rowNumber(), "单位编码",
                    "单位不存在: " + unitCode, unitCode);
        }
        return unit;
    }

    private BillOfMaterial resolveBom(PurchaseOrderItemHeader itemHeader,
                                      Map<String, BillOfMaterial> bomCache) {
        String materialCode = trimOrNull(itemHeader.materialCode());
        String bomVersion = trimOrNull(itemHeader.bomVersion());
        if (materialCode == null || bomVersion == null) {
            return null;
        }
        return bomCache.get(materialCode + ":" + bomVersion);
    }

    private Boolean parsePlanConfirm(String value) {
        if (value == null || value.trim().isEmpty()) {
            return false;
        }
        String normalized = value.trim().toUpperCase();
        return "TRUE".equals(normalized) || "1".equals(normalized) || "是".equals(normalized);
    }

    private Unit resolveSalUnit(PurchaseOrderItemHeader itemHeader,
                                Map<String, Unit> unitCache) {
        String salUnitCode = trimOrNull(itemHeader.salUnitCode());
        if (salUnitCode == null) {
            return null;
        }
        return unitCache.get(salUnitCode);
    }

    private SubReqOrderItem resolveSubReqOrderItem(Material material,
                                                    Integer sequence,
                                                    int rowNumber,
                                                    List<ImportError> errors) {
        if (material == null || sequence == null) {
            logger.debug("resolveSubReqOrderItem: material或sequence为null，material={}, sequence={}", material, sequence);
            return null;
        }
        try {
            logger.debug("查找委外订单明细：物料ID={}, 物料编码={}, sequence={}", material.getId(), material.getCode(), sequence);
            List<SubReqOrderItem> items = subReqOrderItemRepository.findByMaterialIdAndSequence(material.getId(), sequence);
            logger.debug("查询结果：找到{}个委外订单明细", items.size());
            
            if (items.isEmpty()) {
                String errorMsg = String.format("未找到对应的委外订单明细：物料编码=%s, sequence=%d", material.getCode(), sequence);
                logger.warn("{}，行号: {}", errorMsg, rowNumber);
                addError(errors, "采购订单明细", rowNumber, "委外订单分录内码", errorMsg, String.valueOf(sequence));
                return null;
            }
            if (items.size() > 1) {
                logger.warn("找到多个委外订单明细，物料ID={}, sequence={}，使用第一个", material.getId(), sequence);
                addError(errors, "采购订单明细", rowNumber, "委外订单分录内码",
                        String.format("找到多个委外订单明细，物料编码=%s, sequence=%d，使用第一个", material.getCode(), sequence), String.valueOf(sequence));
            }
            SubReqOrderItem foundItem = items.get(0);
            logger.debug("成功找到委外订单明细：ID={}, sequence={}, 委外订单ID={}", 
                    foundItem.getId(), foundItem.getSequence(), 
                    foundItem.getSubReqOrder() != null ? foundItem.getSubReqOrder().getId() : null);
            return foundItem;
        } catch (Exception e) {
            logger.error("查找委外订单明细失败，物料ID={}, sequence={}", material.getId(), sequence, e);
            addError(errors, "采购订单明细", rowNumber, "委外订单分录内码",
                    String.format("查找委外订单明细失败：%s", e.getMessage()), String.valueOf(sequence));
            return null;
        }
    }

    private BigDecimal parseRequiredDecimal(String value,
                                            int rowNumber,
                                            String field,
                                            List<ImportError> errors) {
        if (value == null || value.trim().isEmpty()) {
            addError(errors, "采购订单明细", rowNumber, field, field + "为空", null);
            return null;
        }
        try {
            return new BigDecimal(value.trim().replace(",", ""));
        } catch (Exception e) {
            addError(errors, "采购订单明细", rowNumber, field,
                    "数量格式错误: " + value, value);
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

    private LocalDate parseDate(String value,
                                int rowNumber,
                                List<ImportError> errors) {
        if (value == null || value.trim().isEmpty()) {
            addError(errors, "采购订单", rowNumber, "日期", "订单日期为空", null);
            return null;
        }
        try {
            String dateStr = value.trim();
            if (dateStr.contains("/")) {
                dateStr = dateStr.replace("/", "-");
            }
            return LocalDate.parse(dateStr, DATE_FORMATTER);
        } catch (Exception e) {
            addError(errors, "采购订单", rowNumber, "日期",
                    "日期格式错误: " + value, value);
            return null;
        }
    }

    private Map<String, Supplier> preloadSuppliers(
            Map<String, PurchaseOrderHeader> headerMap,
            List<ImportError> errors) {

        Set<String> supplierCodes = new HashSet<>(Math.max(100, headerMap.size() / 2));
        for (PurchaseOrderHeader header : headerMap.values()) {
            String code = trimOrNull(header.supplierCode());
            if (code != null) {
                supplierCodes.add(code);
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
        int chunkSize = importConfig.getBatch().getQueryChunkSize();
        for (int i = 0; i < codesToQuery.size(); i += chunkSize) {
            int end = Math.min(i + chunkSize, codesToQuery.size());
            List<String> chunk = codesToQuery.subList(i, end);
            List<Supplier> suppliers = supplierRepository.findByCodeIn(chunk);
            for (Supplier supplier : suppliers) {
                supplierCache.put(supplier.getCode(), supplier);
            }
        }

        for (String code : supplierCodes) {
            if (!supplierCache.containsKey(code)) {
                addError(errors, "供应商", 0, "供应商编码",
                        String.format("供应商不存在: %s", code), code);
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        logger.info("供应商预加载完成：共 {} 个，找到 {} 个，耗时 {}ms",
                supplierCodes.size(), supplierCache.size(), duration);
        return supplierCache;
    }

    private void preloadMaterialsUnitsAndBoms(
            Map<String, Map<Integer, PurchaseOrderItemHeader>> itemHeaderMap,
            Map<String, Material> materialCache,
            Map<String, Unit> unitCache,
            Map<String, BillOfMaterial> bomCache) {

        int estimatedItemCount = itemHeaderMap.values().stream().mapToInt(Map::size).sum();
        Set<String> materialCodes = new HashSet<>(Math.max(100, estimatedItemCount / 2));
        Set<String> unitCodes = new HashSet<>(Math.max(50, estimatedItemCount / 10));
        Set<String> salUnitCodes = new HashSet<>(Math.max(50, estimatedItemCount / 10));
        Map<String, String> bomKeys = new HashMap<>(Math.max(100, estimatedItemCount / 10));

        for (Map<Integer, PurchaseOrderItemHeader> items : itemHeaderMap.values()) {
            for (PurchaseOrderItemHeader item : items.values()) {
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
                String salUnitCode = trimOrNull(item.salUnitCode());
                if (salUnitCode != null) {
                    salUnitCodes.add(salUnitCode);
                }
            }
        }

        int chunkSize = importConfig.getBatch().getQueryChunkSize();

        if (!materialCodes.isEmpty()) {
            List<String> materialCodeList = new ArrayList<>(materialCodes);
            for (int i = 0; i < materialCodeList.size(); i += chunkSize) {
                int end = Math.min(i + chunkSize, materialCodeList.size());
                List<String> chunk = materialCodeList.subList(i, end);
                List<Material> materials = materialRepository.findByCodeIn(chunk);
                for (Material material : materials) {
                    materialCache.put(material.getCode(), material);
                }
            }
        }

        Set<String> allUnitCodes = new HashSet<>(unitCodes.size() + salUnitCodes.size());
        allUnitCodes.addAll(unitCodes);
        allUnitCodes.addAll(salUnitCodes);
        if (!allUnitCodes.isEmpty()) {
            List<String> unitCodeList = new ArrayList<>(allUnitCodes);
            for (int i = 0; i < unitCodeList.size(); i += chunkSize) {
                int end = Math.min(i + chunkSize, unitCodeList.size());
                List<String> chunk = unitCodeList.subList(i, end);
                List<Unit> units = unitRepository.findByCodeIn(chunk);
                for (Unit unit : units) {
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

    private Map<String, PurchaseOrder> preloadExistingOrders(Set<String> billNos) {
        Map<String, PurchaseOrder> existingOrderMap = new HashMap<>();
        for (String billNo : billNos) {
            purchaseOrderRepository.findByBillNo(billNo).ifPresent(order -> existingOrderMap.put(billNo, order));
        }
        return existingOrderMap;
    }

    private List<ImportError> buildBatchErrors(
            List<Map.Entry<String, Map<Integer, PurchaseOrderItemHeader>>> batch,
            Map<String, PurchaseOrderHeader> headerMap,
            String message) {
        List<ImportError> batchErrors = new ArrayList<>();
        int maxErrorCount = importConfig.getError().getMaxErrorCount();
        for (Map.Entry<String, Map<Integer, PurchaseOrderItemHeader>> entry : batch) {
            PurchaseOrderHeader header = headerMap.get(entry.getKey());
            if (header != null && batchErrors.size() < maxErrorCount) {
                batchErrors.add(new ImportError(
                        "采购订单", header.rowNumber(), "单据编号", message, header.billNo()));
            }
        }
        return batchErrors;
    }

    private void addError(List<ImportError> errors,
                          String section,
                          int rowNumber,
                          String field,
                          String message,
                          String originalValue) {
        if (errors.size() < importConfig.getError().getMaxErrorCount()) {
            errors.add(new ImportError(section, rowNumber, field, message, originalValue));
        }
    }

    private String trimOrNull(String str) {
        if (str == null) {
            return null;
        }
        String trimmed = str.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record PurchaseOrderHeader(
            int rowNumber,
            String billNo,
            String orderDate,
            String note,
            String supplierCode,
            String supplierName
    ) {
    }

    private record PurchaseOrderItemHeader(
            int rowNumber,
            Integer sequence,
            String materialCode,
            String bomVersion,
            String materialDesc,
            String unitCode,
            String qty,
            String planConfirm,
            String salUnitCode,
            String salQty,
            String salJoinQty,
            String baseSalJoinQty,
            String remarks,
            String salBaseQty,
            Integer subReqOrderSequence
    ) {
    }

    private record PurchaseOrderData(
            PurchaseOrderHeader header,
            PurchaseOrderItemHeader itemHeader
    ) {
    }

    private record OrderItemData(
            String billNo,
            Integer sequence,
            PurchaseOrderItem item,
            int rowNumber
    ) {
    }

    private record BatchResult(
            int successCount,
            List<ImportError> errors
    ) {
    }
}
