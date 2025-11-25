package com.sambound.erp.service.importing.purchase;

import cn.idev.excel.FastExcel;
import cn.idev.excel.context.AnalysisContext;
import cn.idev.excel.read.listener.ReadListener;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.InputStream;
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
    private static final int MAX_ERROR_COUNT = 1000;
    private static final int BATCH_SIZE = 100;
    private static final int MAX_CONCURRENT_BATCHES = 10;
    // 流式处理时的批次大小，达到此大小时立即处理
    private static final int STREAM_BATCH_SIZE = 200;
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

    private final List<PurchaseOrderData> orderDataList = new ArrayList<>();
    private final List<PurchaseOrderData> currentBatch = new ArrayList<>();
    private final AtomicInteger totalRows = new AtomicInteger(0);
    private final List<CompletableFuture<BatchResult>> futures = new ArrayList<>();
    private PurchaseOrderHeader currentHeader;

    public PurchaseOrderImportProcessor(PurchaseOrderRepository purchaseOrderRepository,
                                        PurchaseOrderItemRepository purchaseOrderItemRepository,
                                        SupplierRepository supplierRepository,
                                        MaterialRepository materialRepository,
                                        UnitRepository unitRepository,
                                        BillOfMaterialRepository bomRepository,
                                        SubReqOrderItemRepository subReqOrderItemRepository,
                                        TransactionTemplate transactionTemplate,
                                        ExecutorService executorService) {
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.purchaseOrderItemRepository = purchaseOrderItemRepository;
        this.supplierRepository = supplierRepository;
        this.materialRepository = materialRepository;
        this.unitRepository = unitRepository;
        this.bomRepository = bomRepository;
        this.subReqOrderItemRepository = subReqOrderItemRepository;
        this.transactionTemplate = transactionTemplate;
        this.executorService = executorService;
    }

    public PurchaseOrderImportResponse process(InputStream inputStream) {
        orderDataList.clear();
        totalRows.set(0);
        currentHeader = null;

        FastExcel.read(inputStream, PurchaseOrderExcelRow.class, this)
                .sheet("采购订单#基本信息(FBillHead)")
                .headRowNumber(2)
                .doRead();

        return importToDatabase();
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
            PurchaseOrderData orderData = new PurchaseOrderData(currentHeader, itemHeader);
            orderDataList.add(orderData);
            currentBatch.add(orderData);

            // 达到批次大小时立即处理
            if (currentBatch.size() >= STREAM_BATCH_SIZE) {
                processBatchImmediately();
            }
        }
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext context) {
        logger.debug("采购订单数据收集完成，共 {} 条订单明细数据", orderDataList.size());
        // 处理剩余数据
        if (!currentBatch.isEmpty()) {
            processBatchImmediately();
        }
    }

    /**
     * 立即处理当前批次（流式处理）
     */
    private void processBatchImmediately() {
        if (currentBatch.isEmpty()) {
            return;
        }

        List<PurchaseOrderData> batchToProcess = new ArrayList<>(currentBatch);
        currentBatch.clear();

        logger.debug("流式处理采购订单批次，共 {} 条数据", batchToProcess.size());

        // 异步处理批次
        CompletableFuture<BatchResult> future = CompletableFuture.supplyAsync(() -> {
            try {
                return processBatch(batchToProcess);
            } catch (Exception e) {
                logger.error("批次处理异常", e);
                return new BatchResult(0, List.of(new ImportError(
                        "采购订单", 0, null, "批次处理异常: " + e.getMessage())));
            }
        }, executorService);
        futures.add(future);
    }

    /**
     * 处理单个批次
     */
    private BatchResult processBatch(List<PurchaseOrderData> batch) {
        // 构建订单数据结构（与原有逻辑类似）
        Map<String, PurchaseOrderHeader> headerMap = new LinkedHashMap<>();
        Map<String, Map<Integer, PurchaseOrderItemHeader>> itemHeaderMap = new LinkedHashMap<>();

        for (PurchaseOrderData data : batch) {
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

        if (headerMap.isEmpty()) {
            return new BatchResult(0, List.of());
        }

        List<ImportError> errors = new ArrayList<>();
        AtomicInteger successCount = new AtomicInteger(0);

        // 预加载当前批次需要的数据
        Map<String, Supplier> supplierCache = preloadSuppliers(headerMap, errors);
        Map<String, Material> materialCache = new HashMap<>();
        Map<String, Unit> unitCache = new HashMap<>();
        Map<String, BillOfMaterial> bomCache = new HashMap<>();
        preloadMaterialsUnitsAndBoms(itemHeaderMap, materialCache, unitCache, bomCache);
        Map<String, PurchaseOrder> existingOrderMap = preloadExistingOrders(headerMap.keySet());

        List<Map.Entry<String, Map<Integer, PurchaseOrderItemHeader>>> orderList =
                new ArrayList<>(itemHeaderMap.entrySet());

        // 在事务中处理批次
        int batchSuccess = transactionTemplate.execute(status ->
                importBatchOrders(orderList, headerMap,
                        supplierCache, materialCache, unitCache, bomCache,
                        existingOrderMap, errors)
        );

        successCount.set(batchSuccess);
        return new BatchResult(batchSuccess, errors);
    }

    private PurchaseOrderImportResponse importToDatabase() {
        if (futures.isEmpty()) {
            logger.debug("未找到采购订单数据");
            return new PurchaseOrderImportResponse(
                    new PurchaseOrderImportResponse.PurchaseOrderImportResult(0, 0, 0, new ArrayList<>())
            );
        }

        long startTime = System.currentTimeMillis();
        List<ImportError> errors = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger successCount = new AtomicInteger(0);

        // 等待所有异步批次完成
        waitForBatches(futures, successCount, errors);

        long totalDuration = System.currentTimeMillis() - startTime;
        int totalOrderCount = orderDataList.size();
        logger.info("采购订单导入完成：总耗时 {}ms，总计 {} 条，成功 {} 条，失败 {} 条",
                totalDuration, totalOrderCount, successCount.get(), totalOrderCount - successCount.get());

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
                            "采购订单必须至少有一个订单明细，订单编号: " + billNo);
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
                                "处理失败: " + e.getMessage());
                    }
                }
            } catch (Exception e) {
                logger.error("处理订单失败，单据编号: {}", header.billNo(), e);
                addError(errors, "采购订单", header.rowNumber(), "单据编号",
                        "处理失败: " + e.getMessage());
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
                            "批量保存失败: " + e.getMessage());
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
            addError(errors, "采购订单", header.rowNumber(), "供应商编码", "供应商编码为空");
            return null;
        }
        Supplier supplier = supplierCache.get(supplierCode);
        if (supplier == null) {
            addError(errors, "采购订单", header.rowNumber(), "供应商编码",
                    "供应商不存在: " + supplierCode);
        }
        return supplier;
    }

    private Material resolveMaterial(PurchaseOrderItemHeader itemHeader,
                                     Map<String, Material> materialCache,
                                     List<ImportError> errors) {
        String materialCode = trimOrNull(itemHeader.materialCode());
        if (materialCode == null) {
            addError(errors, "采购订单明细", itemHeader.rowNumber(), "物料编码", "物料编码为空");
            return null;
        }
        Material material = materialCache.get(materialCode);
        if (material == null) {
            addError(errors, "采购订单明细", itemHeader.rowNumber(), "物料编码",
                    "物料不存在: " + materialCode);
        }
        return material;
    }

    private Unit resolveUnit(PurchaseOrderItemHeader itemHeader,
                             Map<String, Unit> unitCache,
                             List<ImportError> errors) {
        String unitCode = trimOrNull(itemHeader.unitCode());
        if (unitCode == null) {
            addError(errors, "采购订单明细", itemHeader.rowNumber(), "单位编码", "单位编码为空");
            return null;
        }
        Unit unit = unitCache.get(unitCode);
        if (unit == null) {
            addError(errors, "采购订单明细", itemHeader.rowNumber(), "单位编码",
                    "单位不存在: " + unitCode);
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
                addError(errors, "采购订单明细", rowNumber, "委外订单分录内码", errorMsg);
                return null;
            }
            if (items.size() > 1) {
                logger.warn("找到多个委外订单明细，物料ID={}, sequence={}，使用第一个", material.getId(), sequence);
                addError(errors, "采购订单明细", rowNumber, "委外订单分录内码",
                        String.format("找到多个委外订单明细，物料编码=%s, sequence=%d，使用第一个", material.getCode(), sequence));
            }
            SubReqOrderItem foundItem = items.get(0);
            logger.debug("成功找到委外订单明细：ID={}, sequence={}, 委外订单ID={}", 
                    foundItem.getId(), foundItem.getSequence(), 
                    foundItem.getSubReqOrder() != null ? foundItem.getSubReqOrder().getId() : null);
            return foundItem;
        } catch (Exception e) {
            logger.error("查找委外订单明细失败，物料ID={}, sequence={}", material.getId(), sequence, e);
            addError(errors, "采购订单明细", rowNumber, "委外订单分录内码",
                    String.format("查找委外订单明细失败：%s", e.getMessage()));
            return null;
        }
    }

    private BigDecimal parseRequiredDecimal(String value,
                                            int rowNumber,
                                            String field,
                                            List<ImportError> errors) {
        if (value == null || value.trim().isEmpty()) {
            addError(errors, "采购订单明细", rowNumber, field, field + "为空");
            return null;
        }
        try {
            return new BigDecimal(value.trim().replace(",", ""));
        } catch (Exception e) {
            addError(errors, "采购订单明细", rowNumber, field,
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

    private LocalDate parseDate(String value,
                                int rowNumber,
                                List<ImportError> errors) {
        if (value == null || value.trim().isEmpty()) {
            addError(errors, "采购订单", rowNumber, "日期", "订单日期为空");
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
                    "日期格式错误: " + value);
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
            logger.debug("未找到需要查询的供应商");
            return new HashMap<>();
        }

        logger.debug("开始预加载 {} 个供应商", supplierCodes.size());
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
        logger.debug("供应商预加载完成：共 {} 个，找到 {} 个，耗时 {}ms",
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

        Set<String> allUnitCodes = new HashSet<>(unitCodes.size() + salUnitCodes.size());
        allUnitCodes.addAll(unitCodes);
        allUnitCodes.addAll(salUnitCodes);
        if (!allUnitCodes.isEmpty()) {
            List<String> unitCodeList = new ArrayList<>(allUnitCodes);
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
        for (Map.Entry<String, Map<Integer, PurchaseOrderItemHeader>> entry : batch) {
            PurchaseOrderHeader header = headerMap.get(entry.getKey());
            if (header != null && batchErrors.size() < MAX_ERROR_COUNT) {
                batchErrors.add(new ImportError(
                        "采购订单", header.rowNumber(), "单据编号", message));
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

