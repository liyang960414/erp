package com.sambound.erp.service.importing.sale;

import cn.idev.excel.FastExcel;
import cn.idev.excel.context.AnalysisContext;
import cn.idev.excel.read.listener.ReadListener;
import com.sambound.erp.dto.SaleOrderImportResponse;
import com.sambound.erp.entity.Customer;
import com.sambound.erp.entity.Material;
import com.sambound.erp.entity.SaleOrder;
import com.sambound.erp.entity.SaleOrderItem;
import com.sambound.erp.entity.Unit;
import com.sambound.erp.repository.CustomerRepository;
import com.sambound.erp.repository.MaterialRepository;
import com.sambound.erp.repository.SaleOrderItemRepository;
import com.sambound.erp.repository.SaleOrderRepository;
import com.sambound.erp.repository.UnitRepository;
import com.sambound.erp.service.CustomerService;
import com.sambound.erp.service.importing.ImportError;
import com.sambound.erp.service.importing.dto.SaleOrderExcelRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
 * 销售订单 Excel 导入处理器。
 */
public class SaleOrderImportProcessor implements ReadListener<SaleOrderExcelRow> {

    private static final Logger logger = LoggerFactory.getLogger(SaleOrderImportProcessor.class);
    private static final int MAX_ERROR_COUNT = 1000;
    private static final int BATCH_SIZE = 100;
    private static final int MAX_CONCURRENT_BATCHES = 10;
    // 流式处理时的批次大小，达到此大小时立即处理
    private static final int STREAM_BATCH_SIZE = 200;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final SaleOrderRepository saleOrderRepository;
    private final SaleOrderItemRepository saleOrderItemRepository;
    private final CustomerRepository customerRepository;
    private final MaterialRepository materialRepository;
    private final UnitRepository unitRepository;
    private final CustomerService customerService;
    private final TransactionTemplate transactionTemplate;
    private final ExecutorService executorService;

    private final List<SaleOrderData> orderDataList = new ArrayList<>();
    private final List<SaleOrderData> currentBatch = new ArrayList<>();
    private final AtomicInteger totalRows = new AtomicInteger(0);
    private final List<CompletableFuture<BatchResult>> futures = new ArrayList<>();
    private SaleOrderHeader currentHeader;

    public SaleOrderImportProcessor(
            SaleOrderRepository saleOrderRepository,
            SaleOrderItemRepository saleOrderItemRepository,
            CustomerRepository customerRepository,
            MaterialRepository materialRepository,
            UnitRepository unitRepository,
            CustomerService customerService,
            TransactionTemplate transactionTemplate,
            ExecutorService executorService) {
        this.saleOrderRepository = saleOrderRepository;
        this.saleOrderItemRepository = saleOrderItemRepository;
        this.customerRepository = customerRepository;
        this.materialRepository = materialRepository;
        this.unitRepository = unitRepository;
        this.customerService = customerService;
        this.transactionTemplate = transactionTemplate;
        this.executorService = executorService;
    }

    /**
     * 从输入流处理导入（新方法，支持流式读取）
     */
    public SaleOrderImportResponse process(InputStream inputStream) {
        FastExcel.read(inputStream, SaleOrderExcelRow.class, this)
                .sheet()
                .headRowNumber(2)
                .doRead();

        return importToDatabase();
    }

    @Override
    public void invoke(SaleOrderExcelRow data, AnalysisContext context) {
        totalRows.incrementAndGet();
        int rowNum = context.readRowHolder().getRowIndex();

        String billNo = trimToNull(data.getBillNo());
        if (billNo != null) {
            currentHeader = new SaleOrderHeader(
                    rowNum,
                    billNo,
                    data.getOrderDate(),
                    data.getNote(),
                    data.getWoNumber(),
                    data.getCustomerCode(),
                    data.getCustomerName()
            );
        }

        if (currentHeader != null && trimToNull(data.getSaleOrderEntry()) != null) {
            if (billNo != null) {
                currentHeader = new SaleOrderHeader(
                        rowNum,
                        billNo,
                        data.getOrderDate() != null ? data.getOrderDate() : currentHeader.orderDate(),
                        data.getNote() != null ? data.getNote() : currentHeader.note(),
                        data.getWoNumber() != null ? data.getWoNumber() : currentHeader.woNumber(),
                        data.getCustomerCode() != null ? data.getCustomerCode() : currentHeader.customerCode(),
                        data.getCustomerName() != null ? data.getCustomerName() : currentHeader.customerName()
                );
            }

            SaleOrderItemData itemData = new SaleOrderItemData(
                    rowNum,
                    parseInteger(data.getSaleOrderEntry()),
                    data.getMaterialCode(),
                    data.getUnitCode(),
                    data.getQty(),
                    data.getOldQty(),
                    data.getInspectionDate(),
                    data.getDeliveryDate(),
                    data.getBomVersion(),
                    data.getEntryNote()
            );
            SaleOrderData orderData = new SaleOrderData(currentHeader, itemData);
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
        logger.info("销售订单数据收集完成，共 {} 条明细", orderDataList.size());
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

        List<SaleOrderData> batchToProcess = new ArrayList<>(currentBatch);
        currentBatch.clear();

        logger.debug("流式处理销售订单批次，共 {} 条数据", batchToProcess.size());

        // 异步处理批次
        CompletableFuture<BatchResult> future = CompletableFuture.supplyAsync(() -> {
            try {
                return processBatch(batchToProcess);
            } catch (Exception e) {
                logger.error("批次处理异常", e);
                return new BatchResult(0, List.of(new ImportError(
                        "销售订单", 0, null, "批次处理异常: " + e.getMessage())));
            }
        }, executorService);
        futures.add(future);
    }

    /**
     * 处理单个批次
     */
    private BatchResult processBatch(List<SaleOrderData> batch) {
        Map<SaleOrderHeader, List<SaleOrderItemData>> orderGroups = groupByHeader(batch);
        if (orderGroups.isEmpty()) {
            return new BatchResult(0, List.of());
        }

        List<ImportError> errors = new ArrayList<>();
        AtomicInteger successCount = new AtomicInteger(0);

        // 预加载当前批次需要的数据
        Map<String, Customer> customerCache = preloadAndCreateCustomers(orderGroups, errors);
        Map<String, Material> materialCache = new HashMap<>();
        Map<String, Unit> unitCache = new HashMap<>();
        preloadMaterialsAndUnits(orderGroups, materialCache, unitCache);
        Map<String, SaleOrder> existingOrderMap = preloadExistingOrders(orderGroups);

        List<Map.Entry<SaleOrderHeader, List<SaleOrderItemData>>> orderList =
                new ArrayList<>(orderGroups.entrySet());

        // 在事务中处理批次
        int batchSuccess = transactionTemplate.execute(status ->
                importBatchOrders(orderList, customerCache, materialCache, unitCache,
                        existingOrderMap, errors)
        );

        return new BatchResult(batchSuccess, errors);
    }

    private SaleOrderImportResponse importToDatabase() {
        if (futures.isEmpty()) {
            return new SaleOrderImportResponse(
                    new SaleOrderImportResponse.SaleOrderImportResult(0, 0, 0, new ArrayList<>())
            );
        }

        long startTime = System.currentTimeMillis();
        List<ImportError> errors = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger successCount = new AtomicInteger(0);

        // 等待所有异步批次完成
        waitForBatches(futures, successCount, errors);

        long totalDuration = System.currentTimeMillis() - startTime;
        int totalOrderCount = orderDataList.size();
        logger.info("销售订单导入完成：总耗时 {}ms，总计 {} 条，成功 {} 条，失败 {} 条",
                totalDuration, totalOrderCount, successCount.get(), totalOrderCount - successCount.get());

        return new SaleOrderImportResponse(
                new SaleOrderImportResponse.SaleOrderImportResult(
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
            List<Map.Entry<SaleOrderHeader, List<SaleOrderItemData>>> batch,
            Map<String, Customer> customerCache,
            Map<String, Material> materialCache,
            Map<String, Unit> unitCache,
            Map<String, SaleOrder> existingOrderMap,
            List<ImportError> errors) {

        int successCount = 0;

        for (Map.Entry<SaleOrderHeader, List<SaleOrderItemData>> entry : batch) {
            SaleOrderHeader header = entry.getKey();
            List<SaleOrderItemData> items = entry.getValue();

            try {
                SaleOrder saleOrder = existingOrderMap.get(header.billNo());
                boolean isNewOrder = (saleOrder == null);

                if (isNewOrder) {
                    Customer customer = resolveCustomer(header, customerCache, errors);
                    if (customer == null) {
                        continue;
                    }

                    LocalDate orderDate = parseDate(header.orderDate(), "订单日期为空", "日期格式错误", header.rowNumber(), errors);
                    if (orderDate == null) {
                        continue;
                    }

                    saleOrder = SaleOrder.builder()
                            .billNo(header.billNo())
                            .orderDate(orderDate)
                            .note(header.note())
                            .woNumber(header.woNumber())
                            .customer(customer)
                            .build();
                    saleOrder = saleOrderRepository.save(saleOrder);
                    existingOrderMap.put(header.billNo(), saleOrder);
                }

                List<SaleOrderItem> itemsToSave = buildOrderItems(items, saleOrder, materialCache, unitCache, errors);
                if (!itemsToSave.isEmpty()) {
                    saleOrderItemRepository.saveAll(itemsToSave);
                    successCount++;
                }
            } catch (Exception e) {
                logger.error("保存订单失败，单据编号: {}", header.billNo(), e);
                if (errors.size() < MAX_ERROR_COUNT) {
                    errors.add(new ImportError(
                            "销售订单", header.rowNumber(), "单据编号",
                            "保存失败: " + e.getMessage()));
                }
            }
        }

        return successCount;
    }

    private Customer resolveCustomer(SaleOrderHeader header,
                                     Map<String, Customer> customerCache,
                                     List<ImportError> errors) {
        String customerCode = trimToNull(header.customerCode());
        String customerName = trimToNull(header.customerName());

        if (customerCode != null && customerName != null) {
            Customer customer = customerCache.get(customerCode);
            if (customer == null && errors.size() < MAX_ERROR_COUNT) {
                errors.add(new ImportError(
                        "销售订单", header.rowNumber(), "客户编码",
                        "客户不存在: " + customerCode));
            }
            return customer;
        }
        if (errors.size() < MAX_ERROR_COUNT) {
            errors.add(new ImportError(
                    "销售订单", header.rowNumber(), "客户编码",
                    "客户编码或名称为空"));
        }
        return null;
    }

    private List<SaleOrderItem> buildOrderItems(List<SaleOrderItemData> items,
                                                SaleOrder saleOrder,
                                                Map<String, Material> materialCache,
                                                Map<String, Unit> unitCache,
                                                List<ImportError> errors) {
        List<SaleOrderItem> itemsToSave = new ArrayList<>();

        for (SaleOrderItemData itemData : items) {
            try {
                Material material = resolveMaterial(itemData, materialCache, errors);
                if (material == null) {
                    continue;
                }

                Unit unit = resolveUnit(itemData, unitCache, errors);
                if (unit == null) {
                    continue;
                }

                BigDecimal qty = parseBigDecimal(itemData.qty(), "销售数量为空", "数量格式错误", itemData.rowNumber(), errors);
                if (qty == null) {
                    continue;
                }

                BigDecimal oldQty = parseOptionalBigDecimal(itemData.oldQty());
                LocalDate inspectionDate = parseOptionalDate(itemData.inspectionDate());
                LocalDateTime deliveryDate = parseOptionalDateTime(itemData.deliveryDate());
                EntryNote entryNote = parseEntryNote(itemData.entryNote());

                SaleOrderItem item = SaleOrderItem.builder()
                        .saleOrder(saleOrder)
                        .sequence(itemData.sequence() != null ? itemData.sequence() : 1)
                        .material(material)
                        .unit(unit)
                        .qty(qty)
                        .oldQty(oldQty)
                        .inspectionDate(inspectionDate)
                        .deliveryDate(deliveryDate)
                        .bomVersion(itemData.bomVersion())
                        .entryNote(entryNote.cleanedEntryNote())
                        .customerOrderNo(entryNote.customerOrderNo())
                        .customerLineNo(entryNote.customerLineNo())
                        .build();

                itemsToSave.add(item);
            } catch (Exception e) {
                logger.error("处理订单明细失败，行号: {}", itemData.rowNumber(), e);
                if (errors.size() < MAX_ERROR_COUNT) {
                    errors.add(new ImportError(
                            "销售订单明细", itemData.rowNumber(), null,
                            "处理失败: " + e.getMessage()));
                }
            }
        }

        return itemsToSave;
    }

    private Material resolveMaterial(SaleOrderItemData itemData,
                                     Map<String, Material> materialCache,
                                     List<ImportError> errors) {
        String materialCode = trimToNull(itemData.materialCode());
        if (materialCode == null) {
            if (errors.size() < MAX_ERROR_COUNT) {
                errors.add(new ImportError(
                        "销售订单明细", itemData.rowNumber(), "物料编码",
                        "物料编码为空"));
            }
            return null;
        }

        Material material = materialCache.get(materialCode);
        if (material == null && errors.size() < MAX_ERROR_COUNT) {
            errors.add(new ImportError(
                    "销售订单明细", itemData.rowNumber(), "物料编码",
                    "物料不存在: " + materialCode));
        }
        return material;
    }

    private Unit resolveUnit(SaleOrderItemData itemData,
                             Map<String, Unit> unitCache,
                             List<ImportError> errors) {
        String unitCode = trimToNull(itemData.unitCode());
        if (unitCode == null) {
            if (errors.size() < MAX_ERROR_COUNT) {
                errors.add(new ImportError(
                        "销售订单明细", itemData.rowNumber(), "单位编码",
                        "单位编码为空"));
            }
            return null;
        }

        Unit unit = unitCache.get(unitCode);
        if (unit == null && errors.size() < MAX_ERROR_COUNT) {
            errors.add(new ImportError(
                    "销售订单明细", itemData.rowNumber(), "单位编码",
                    "单位不存在: " + unitCode));
        }
        return unit;
    }

    private Map<SaleOrderHeader, List<SaleOrderItemData>> groupByHeader(List<SaleOrderData> dataList) {
        Map<SaleOrderHeader, List<SaleOrderItemData>> orderGroups = new LinkedHashMap<>();
        for (SaleOrderData data : dataList) {
            orderGroups.computeIfAbsent(data.header(), k -> new ArrayList<>()).add(data.item());
        }
        return orderGroups;
    }

    private Map<String, Customer> preloadAndCreateCustomers(
            Map<SaleOrderHeader, List<SaleOrderItemData>> orderGroups,
            List<ImportError> errors) {

        Map<String, String> customerCodeToName = new LinkedHashMap<>();
        for (SaleOrderHeader header : orderGroups.keySet()) {
            String code = trimToNull(header.customerCode());
            String name = trimToNull(header.customerName());
            if (code != null && name != null) {
                customerCodeToName.putIfAbsent(code, name);
            }
        }

        if (customerCodeToName.isEmpty()) {
            return new HashMap<>();
        }

        Map<String, Customer> customerCache = new HashMap<>();
        List<String> codesToQuery = new ArrayList<>(customerCodeToName.keySet());
        for (int i = 0; i < codesToQuery.size(); i += 1000) {
            int end = Math.min(i + 1000, codesToQuery.size());
            List<Customer> existingCustomers = customerRepository.findByCodeIn(codesToQuery.subList(i, end));
            for (Customer customer : existingCustomers) {
                customerCache.put(customer.getCode(), customer);
            }
        }

        List<String> codesToCreate = new ArrayList<>();
        List<String> namesToCreate = new ArrayList<>();
        for (Map.Entry<String, String> entry : customerCodeToName.entrySet()) {
            if (!customerCache.containsKey(entry.getKey())) {
                codesToCreate.add(entry.getKey());
                namesToCreate.add(entry.getValue());
            }
        }

        if (!codesToCreate.isEmpty()) {
            try {
                transactionTemplate.executeWithoutResult(status -> {
                    for (int i = 0; i < codesToCreate.size(); i++) {
                        try {
                            Customer customer = customerRepository.insertOrGetByCode(
                                    codesToCreate.get(i), namesToCreate.get(i));
                            customerCache.put(customer.getCode(), customer);
                        } catch (Exception e) {
                            logger.error("创建客户失败: {}", codesToCreate.get(i), e);
                            if (errors.size() < MAX_ERROR_COUNT) {
                                synchronized (errors) {
                                    if (errors.size() < MAX_ERROR_COUNT) {
                                        errors.add(new ImportError(
                                                "客户", 0, "客户编码",
                                                String.format("创建客户失败 %s: %s", codesToCreate.get(i), e.getMessage())));
                                    }
                                }
                            }
                        }
                    }
                });
            } catch (Exception e) {
                logger.error("批量创建客户失败", e);
                if (errors.size() < MAX_ERROR_COUNT) {
                    synchronized (errors) {
                        if (errors.size() < MAX_ERROR_COUNT) {
                            errors.add(new ImportError(
                                    "客户", 0, "客户编码",
                                    "批量创建客户失败: " + e.getMessage()));
                        }
                    }
                }
            }
        }

        return customerCache;
    }

    private void preloadMaterialsAndUnits(
            Map<SaleOrderHeader, List<SaleOrderItemData>> orderGroups,
            Map<String, Material> materialCache,
            Map<String, Unit> unitCache) {

        Set<String> materialCodes = new HashSet<>();
        Set<String> unitCodes = new HashSet<>();

        for (Map.Entry<SaleOrderHeader, List<SaleOrderItemData>> entry : orderGroups.entrySet()) {
            for (SaleOrderItemData item : entry.getValue()) {
                String materialCode = trimToNull(item.materialCode());
                if (materialCode != null) {
                    materialCodes.add(materialCode);
                }
                String unitCode = trimToNull(item.unitCode());
                if (unitCode != null) {
                    unitCodes.add(unitCode);
                }
            }
        }

        if (!materialCodes.isEmpty()) {
            List<String> materialCodeList = new ArrayList<>(materialCodes);
            for (int i = 0; i < materialCodeList.size(); i += 1000) {
                int end = Math.min(i + 1000, materialCodeList.size());
                // 使用 JOIN FETCH 预加载 MaterialGroup 和 baseUnit，避免 LazyInitializationException
                List<Material> materials = materialRepository.findByCodeInWithMaterialGroup(materialCodeList.subList(i, end));
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
                // 使用 JOIN FETCH 预加载 UnitGroup，避免 LazyInitializationException
                List<Unit> units = unitRepository.findByCodeInWithUnitGroup(unitCodeList.subList(i, end));
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
    }

    private Map<String, SaleOrder> preloadExistingOrders(
            Map<SaleOrderHeader, List<SaleOrderItemData>> orderGroups) {

        Set<String> billNos = new HashSet<>();
        for (SaleOrderHeader header : orderGroups.keySet()) {
            String billNo = trimToNull(header.billNo());
            if (billNo != null) {
                billNos.add(billNo);
            }
        }

        Map<String, SaleOrder> existingOrderMap = new HashMap<>();
        for (String billNo : billNos) {
            saleOrderRepository.findByBillNo(billNo).ifPresent(order -> existingOrderMap.put(billNo, order));
        }
        return existingOrderMap;
    }

    private List<ImportError> buildBatchErrors(
            List<Map.Entry<SaleOrderHeader, List<SaleOrderItemData>>> batch,
            String message) {
        List<ImportError> batchErrors = new ArrayList<>();
        for (Map.Entry<SaleOrderHeader, List<SaleOrderItemData>> entry : batch) {
            if (batchErrors.size() < MAX_ERROR_COUNT) {
                batchErrors.add(new ImportError(
                        "销售订单", entry.getKey().rowNumber(), "单据编号", message));
            }
        }
        return batchErrors;
    }

    private Integer parseInteger(String value) {
        try {
            String trimmed = trimToNull(value);
            return trimmed != null ? Integer.parseInt(trimmed) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private BigDecimal parseBigDecimal(String value,
                                       String emptyMessage,
                                       String errorMessage,
                                       int rowNumber,
                                       List<ImportError> errors) {
        if (value == null || value.trim().isEmpty()) {
            if (errors.size() < MAX_ERROR_COUNT) {
                errors.add(new ImportError("销售订单明细", rowNumber, "销售数量", emptyMessage));
            }
            return null;
        }
        try {
            return new BigDecimal(value.trim().replace(",", ""));
        } catch (Exception e) {
            if (errors.size() < MAX_ERROR_COUNT) {
                errors.add(new ImportError("销售订单明细", rowNumber, "销售数量", errorMessage + ": " + value));
            }
            return null;
        }
    }

    private BigDecimal parseOptionalBigDecimal(String value) {
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
                                String emptyMessage,
                                String errorMessage,
                                int rowNumber,
                                List<ImportError> errors) {
        if (value == null || value.trim().isEmpty()) {
            if (errors.size() < MAX_ERROR_COUNT) {
                errors.add(new ImportError("销售订单", rowNumber, "日期", emptyMessage));
            }
            return null;
        }
        try {
            return LocalDate.parse(value.trim(), DATE_FORMATTER);
        } catch (Exception e) {
            if (errors.size() < MAX_ERROR_COUNT) {
                errors.add(new ImportError("销售订单", rowNumber, "日期", errorMessage + ": " + value));
            }
            return null;
        }
    }

    private LocalDate parseOptionalDate(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim(), DATE_FORMATTER);
        } catch (Exception e) {
            return null;
        }
    }

    private LocalDateTime parseOptionalDateTime(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            String trimmed = value.trim();
            if (trimmed.contains(" ")) {
                return LocalDateTime.parse(trimmed, DATETIME_FORMATTER);
            }
            return LocalDate.parse(trimmed, DATE_FORMATTER).atStartOfDay();
        } catch (Exception e) {
            return null;
        }
    }

    private EntryNote parseEntryNote(String entryNote) {
        if (entryNote == null || entryNote.trim().isEmpty()) {
            return new EntryNote(null, null, null);
        }

        String trimmedNote = entryNote.trim();
        int slashIndex = trimmedNote.indexOf('/');
        if (slashIndex > 0 && slashIndex < trimmedNote.length() - 1) {
            String customerOrderNo = trimmedNote.substring(0, slashIndex).trim();
            String customerLineNo = trimmedNote.substring(slashIndex + 1).trim();
            String cleanedEntryNote = trimmedNote.equals(customerOrderNo + "/" + customerLineNo)
                    ? null
                    : trimmedNote.replace(customerOrderNo + "/" + customerLineNo, "").trim();
            if (cleanedEntryNote != null && cleanedEntryNote.isEmpty()) {
                cleanedEntryNote = null;
            }
            return new EntryNote(customerOrderNo, customerLineNo, cleanedEntryNote);
        }

        return new EntryNote(null, null, trimmedNote);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record SaleOrderHeader(
            int rowNumber,
            String billNo,
            String orderDate,
            String note,
            String woNumber,
            String customerCode,
            String customerName
    ) {
    }

    private record SaleOrderItemData(
            int rowNumber,
            Integer sequence,
            String materialCode,
            String unitCode,
            String qty,
            String oldQty,
            String inspectionDate,
            String deliveryDate,
            String bomVersion,
            String entryNote
    ) {
    }

    private record SaleOrderData(
            SaleOrderHeader header,
            SaleOrderItemData item
    ) {
    }

    private record EntryNote(
            String customerOrderNo,
            String customerLineNo,
            String cleanedEntryNote
    ) {
    }

    private record BatchResult(
            int successCount,
            List<ImportError> errors
    ) {
    }
}

