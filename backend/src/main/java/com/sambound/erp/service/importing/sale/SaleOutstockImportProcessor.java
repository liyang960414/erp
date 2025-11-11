package com.sambound.erp.service.importing.sale;

import cn.idev.excel.FastExcel;
import cn.idev.excel.context.AnalysisContext;
import cn.idev.excel.read.listener.ReadListener;
import com.sambound.erp.dto.SaleOutstockImportResponse;
import com.sambound.erp.entity.SaleOrder;
import com.sambound.erp.entity.SaleOrderItem;
import com.sambound.erp.entity.SaleOutstock;
import com.sambound.erp.entity.SaleOutstockItem;
import com.sambound.erp.enums.SaleOrderItemStatus;
import com.sambound.erp.enums.SaleOrderStatus;
import com.sambound.erp.repository.SaleOrderItemRepository;
import com.sambound.erp.repository.SaleOrderRepository;
import com.sambound.erp.repository.SaleOutstockRepository;
import com.sambound.erp.service.importing.ImportError;
import com.sambound.erp.service.importing.dto.SaleOutstockExcelRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.TransactionDefinition;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

public class SaleOutstockImportProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SaleOutstockImportProcessor.class);
    private static final int MAX_ERROR_COUNT = 1000;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final int BATCH_SIZE = 500;
    private static final int IMPORT_BATCH_SIZE = 50;
    private static final int MAX_CONCURRENT_IMPORTS = 10;

    private final SaleOutstockRepository saleOutstockRepository;
    private final SaleOrderItemRepository saleOrderItemRepository;
    private final SaleOrderRepository saleOrderRepository;
    private final TransactionTemplate transactionTemplate;
    private final TransactionTemplate readOnlyTransactionTemplate;
    private final Semaphore importConcurrencySemaphore = new Semaphore(MAX_CONCURRENT_IMPORTS, true);
    private final ExecutorService executorService;

    public SaleOutstockImportProcessor(
            SaleOutstockRepository saleOutstockRepository,
            SaleOrderItemRepository saleOrderItemRepository,
            SaleOrderRepository saleOrderRepository,
            TransactionTemplate transactionTemplate,
            TransactionTemplate readOnlyTransactionTemplate,
            ExecutorService executorService) {
        this.saleOutstockRepository = saleOutstockRepository;
        this.saleOrderItemRepository = saleOrderItemRepository;
        this.saleOrderRepository = saleOrderRepository;
        this.transactionTemplate = transactionTemplate;
        this.readOnlyTransactionTemplate = readOnlyTransactionTemplate;
        this.executorService = executorService;
    }

    public SaleOutstockImportResponse process(byte[] fileBytes) {
        try {
            SaleOutstockDataCollector collector = new SaleOutstockDataCollector();
            FastExcel.read(new ByteArrayInputStream(fileBytes), SaleOutstockExcelRow.class, collector)
                    .sheet()
                    .headRowNumber(2)
                    .doRead();

            SaleOutstockImportResponse result = collector.importToDatabase();
            logger.info("销售出库导入完成：总计 {} 条，成功 {} 条，失败 {} 条",
                    result.result().totalRows(),
                    result.result().successCount(),
                    result.result().failureCount());

            return result;
        } catch (Exception e) {
            logger.error("销售出库Excel导入失败", e);
            throw new RuntimeException("销售出库Excel导入失败: " + e.getMessage(), e);
        }
    }

    private class SaleOutstockDataCollector implements ReadListener<SaleOutstockExcelRow> {

        private final List<SaleOutstockData> outstockDataList = new ArrayList<>();
        private final Map<Integer, String> billNoByRowNumber = new ConcurrentHashMap<>();
        private final AtomicInteger totalRows = new AtomicInteger(0);
        private SaleOutstockHeader currentHeader = null;

        @Override
        public void invoke(SaleOutstockExcelRow data, AnalysisContext context) {
            totalRows.incrementAndGet();
            int rowNum = context.readRowHolder().getRowIndex();

            String billNo = trimToNull(data.getBillNo());
            if (billNo != null) {
                currentHeader = new SaleOutstockHeader(
                        rowNum,
                        billNo,
                        trimToNull(data.getOutstockDate()),
                        trimToNull(data.getNote())
                );
            } else if (currentHeader != null) {
                // 对于续行，补齐缺失的头部信息
                currentHeader = new SaleOutstockHeader(
                        currentHeader.rowNumber(),
                        currentHeader.billNo(),
                        data.getOutstockDate() != null ? trimToNull(data.getOutstockDate()) : currentHeader.outstockDate(),
                        data.getNote() != null ? trimToNull(data.getNote()) : currentHeader.note()
                );
            }

            if (currentHeader == null) {
                return;
            }

            String currentBillNo = trimToNull(currentHeader.billNo());
            if (currentBillNo != null) {
                billNoByRowNumber.put(rowNum, currentBillNo);
            }

            String saleOrderEntryId = trimToNull(data.getSaleOrderEntryId());
            if (saleOrderEntryId != null) {
                //移除千分位
                SaleOutstockItemData item = new SaleOutstockItemData(
                        rowNum,
                        trimToNull(data.getEntrySequence()),
                        trimToNull(data.getMaterialCode()),
                        trimToNull(data.getMaterialName()),
                        trimToNull(data.getUnitCode()),
                        trimToNull(data.getUnitName()),
                        trimToNull(data.getRealQty()),
                        trimToNull(data.getEntryNote()),
                        trimToNull(data.getWoNumber()),
                        saleOrderEntryId
                );

                outstockDataList.add(new SaleOutstockData(currentHeader, item));
                if (currentBillNo != null) {
                    billNoByRowNumber.put(item.rowNumber(), currentBillNo);
                }
            }
        }

        @Override
        public void doAfterAllAnalysed(AnalysisContext context) {
            logger.info("销售出库数据收集完成，共 {} 条出库明细数据", outstockDataList.size());
        }

        public SaleOutstockImportResponse importToDatabase() {
            if (outstockDataList.isEmpty()) {
                logger.info("未找到销售出库数据");
                return new SaleOutstockImportResponse(
                        new SaleOutstockImportResponse.SaleOutstockImportResult(0, 0, 0, new ArrayList<>())
                );
            }

            Map<SaleOutstockHeader, List<SaleOutstockItemData>> outstockGroups = new LinkedHashMap<>();
            for (SaleOutstockData data : outstockDataList) {
                outstockGroups.computeIfAbsent(data.header(), k -> new ArrayList<>()).add(data.item());
            }

            Map<String, List<SaleOutstockHeader>> headersByBillNo = new HashMap<>();
            for (SaleOutstockHeader header : outstockGroups.keySet()) {
                String billNo = trimToNull(header.billNo());
                if (billNo != null) {
                    headersByBillNo.computeIfAbsent(billNo, k -> new ArrayList<>()).add(header);
                }
            }

            Map<Integer, String> duplicateBillNoByRow = new HashMap<>();
            headersByBillNo.forEach((billNo, headerList) -> {
                if (headerList.size() > 1) {
                    headerList.forEach(header -> duplicateBillNoByRow.put(header.rowNumber(), billNo));
                }
            });

            Set<String> billNos = headersByBillNo.keySet();
            Set<String> existingBillNos = findExistingBillNos(billNos);

            List<Map.Entry<SaleOutstockHeader, List<SaleOutstockItemData>>> entries =
                    new ArrayList<>(outstockGroups.entrySet());

            int totalOutstockCount = entries.size();
            logger.info("找到 {} 张销售出库单，{} 条明细，开始导入数据库", totalOutstockCount, outstockDataList.size());

            List<ImportError> errors = Collections.synchronizedList(new ArrayList<>());
            AtomicInteger successCount = new AtomicInteger(0);
            Set<Long> saleOrdersToRefresh = ConcurrentHashMap.newKeySet();
            Map<Long, Integer> saleOrderFirstRowMap = new ConcurrentHashMap<>();
            ConcurrentHashMap<Integer, ReentrantLock> sequenceLocks = new ConcurrentHashMap<>();

            for (int start = 0; start < entries.size() && errors.size() < MAX_ERROR_COUNT; start += IMPORT_BATCH_SIZE) {
                int end = Math.min(start + IMPORT_BATCH_SIZE, entries.size());
                List<Map.Entry<SaleOutstockHeader, List<SaleOutstockItemData>>> batch = entries.subList(start, end);

                List<Future<ImportTaskResult>> futures = new ArrayList<>();
                Map<Future<ImportTaskResult>, SaleOutstockHeader> futureHeaders = new HashMap<>();

                    for (Map.Entry<SaleOutstockHeader, List<SaleOutstockItemData>> entry : batch) {
                        if (errors.size() >= MAX_ERROR_COUNT) {
                            break;
                        }

                        SaleOutstockHeader header = entry.getKey();
                        List<SaleOutstockItemData> items = entry.getValue();
                        String billNo = trimToNull(header.billNo());

                        if (billNo != null) {
                            if (duplicateBillNoByRow.containsKey(header.rowNumber())) {
                                addError(errors, header.rowNumber(), "单据编号",
                                        "Excel 中存在重复的销售出库单编号: " + billNo);
                                continue;
                            }
                            if (existingBillNos.contains(billNo)) {
                                addError(errors, header.rowNumber(), "单据编号",
                                        "销售出库单已存在: " + billNo);
                                continue;
                            }
                        }

                        Callable<ImportTaskResult> task = () -> {
                            importConcurrencySemaphore.acquireUninterruptibly();
                            try {
                                Set<Long> affectedSaleOrders = transactionTemplate.execute(status ->
                                        processOutstock(header, items, sequenceLocks));
                                return new ImportTaskResult(
                                        affectedSaleOrders == null ? Collections.emptySet() : affectedSaleOrders
                                );
                            } finally {
                                importConcurrencySemaphore.release();
                            }
                        };

                        Future<ImportTaskResult> future = executorService.submit(task);
                        futures.add(future);
                        futureHeaders.put(future, header);
                    }

                for (Future<ImportTaskResult> future : futures) {
                    if (errors.size() >= MAX_ERROR_COUNT) {
                        future.cancel(false);
                        continue;
                    }

                    SaleOutstockHeader header = futureHeaders.get(future);
                    String billNo = header != null ? trimToNull(header.billNo()) : null;
                    try {
                        ImportTaskResult result = future.get();
                        successCount.incrementAndGet();
                        if (result.affectedSaleOrderIds() != null) {
                            result.affectedSaleOrderIds().forEach(id -> {
                                saleOrdersToRefresh.add(id);
                                if (header != null) {
                                    saleOrderFirstRowMap.putIfAbsent(id, header.rowNumber());
                                }
                            });
                        }
                    } catch (Exception e) {
                        Throwable cause = e.getCause() != null ? e.getCause() : e;
                        if (cause instanceof ImportException importException) {
                            addError(errors, importException.rowNumber, importException.field, importException.getMessage());
                        } else {
                            logger.error("导入销售出库单失败，单据编号: {}", billNo, cause);
                            if (header != null) {
                                addError(errors, header.rowNumber(), "单据编号", "导入失败: " + cause.getMessage());
                            }
                        }
                    }
                }
            }

            if (!saleOrdersToRefresh.isEmpty()) {
                try {
                    refreshSaleOrderStatus(saleOrdersToRefresh, saleOrderFirstRowMap);
                } catch (ImportException e) {
                    addError(errors, e.rowNumber, e.field, e.getMessage());
                }
            }

            int failureCount = totalOutstockCount - successCount.get();
            return new SaleOutstockImportResponse(
                    new SaleOutstockImportResponse.SaleOutstockImportResult(
                            totalOutstockCount,
                            successCount.get(),
                            failureCount,
                            new ArrayList<>(errors)
                    )
            );
        }

        private Set<Long> processOutstock(
                SaleOutstockHeader header,
                List<SaleOutstockItemData> items,
                ConcurrentHashMap<Integer, ReentrantLock> sequenceLocks) {

            String billNo = requireNonBlank(header.billNo(), header.rowNumber(), "单据编号");
            LocalDate outstockDate = parseDate(requireNonBlank(header.outstockDate(), header.rowNumber(), "出库日期"), header.rowNumber());
            String note = trimToNull(header.note());

            if (items.isEmpty()) {
                throw new ImportException(header.rowNumber(), "明细",
                        "销售出库单没有任何明细记录");
            }

            List<ParsedItem> parsedItems = new ArrayList<>();
            LinkedHashSet<Integer> uniqueSequences = new LinkedHashSet<>();

            for (SaleOutstockItemData item : items) {
                int rowNumber = item.rowNumber();
                Integer saleOrderSequence = parseInteger(requireNonBlank(item.saleOrderEntryId(), rowNumber, "销售订单EntryId"), rowNumber, "销售订单EntryId");
                Integer sequence = parseInteger(requireNonBlank(item.entrySequence(), rowNumber, "明细序号"), rowNumber, "明细序号");
                BigDecimal qty = parseDecimal(requireNonBlank(item.realQty(), rowNumber, "实发数量"), rowNumber, "实发数量");

                parsedItems.add(new ParsedItem(item, saleOrderSequence, sequence, qty));
                uniqueSequences.add(saleOrderSequence);
            }

            List<Integer> sortedSequences = new ArrayList<>(uniqueSequences);
            Collections.sort(sortedSequences);

            List<ReentrantLock> locks = new ArrayList<>(sortedSequences.size());
            for (Integer sequence : sortedSequences) {
                ReentrantLock lock = sequenceLocks.computeIfAbsent(sequence, key -> new ReentrantLock(true));
                locks.add(lock);
            }

            for (ReentrantLock lock : locks) {
                lock.lock();
            }

            try {
                return transactionTemplate.execute(status ->
                        executeOutstockTransaction(billNo, outstockDate, note, parsedItems));
            } finally {
                for (int i = locks.size() - 1; i >= 0; i--) {
                    locks.get(i).unlock();
                }
            }
        }

        private Set<Long> executeOutstockTransaction(
                String billNo,
                LocalDate outstockDate,
                String note,
                List<ParsedItem> parsedItems) {

            List<Integer> sequences = parsedItems.stream()
                    .map(ParsedItem::saleOrderSequence)
                    .distinct()
                    .toList();

            List<SaleOrderItem> saleOrderItems = saleOrderItemRepository.findBySequenceInWithRelations(sequences);
            Map<Integer, SaleOrderItem> itemsBySequence = saleOrderItems.stream()
                    .collect(Collectors.toMap(SaleOrderItem::getSequence, item -> item));

            Map<Integer, ItemAggregation> aggregations = new HashMap<>();
            Set<Long> affectedSaleOrderIds = new HashSet<>();

            SaleOutstock outstock = SaleOutstock.builder()
                    .billNo(billNo)
                    .outstockDate(outstockDate)
                    .note(note)
                    .build();

            for (ParsedItem parsed : parsedItems) {
                SaleOrderItem saleOrderItem = itemsBySequence.get(parsed.saleOrderSequence());
                if (saleOrderItem == null) {
                    throw new ImportException(parsed.raw().rowNumber(), "销售订单EntryId",
                            "无法找到对应的销售订单明细，序号: " + parsed.raw().saleOrderEntryId());
                }

                validateMaterial(parsed.raw(), saleOrderItem, parsed.raw().rowNumber());
                validateUnit(parsed.raw(), saleOrderItem, parsed.raw().rowNumber());

                ItemAggregation aggregation = aggregations.computeIfAbsent(
                        parsed.saleOrderSequence(),
                        key -> new ItemAggregation(saleOrderItem));
                aggregation.addQuantity(parsed.qty(), parsed.raw().rowNumber());

                SaleOutstockItem outstockItem = SaleOutstockItem.builder()
                        .sequence(parsed.sequence())
                        .saleOrderItem(saleOrderItem)
                        .material(saleOrderItem.getMaterial())
                        .unit(saleOrderItem.getUnit())
                        .qty(parsed.qty())
                        .entryNote(trimToNull(parsed.raw().entryNote()))
                        .woNumber(trimToNull(parsed.raw().woNumber()))
                        .build();
                outstock.addItem(outstockItem);

                affectedSaleOrderIds.add(saleOrderItem.getSaleOrder().getId());
            }

            aggregations.values().forEach(ItemAggregation::apply);

            saleOutstockRepository.save(outstock);
            if (!aggregations.isEmpty()) {
                saleOrderItemRepository.saveAll(
                        aggregations.values().stream()
                                .map(ItemAggregation::saleOrderItem)
                                .toList());
            }

            return affectedSaleOrderIds;
        }

        private void refreshSaleOrderStatus(Set<Long> saleOrderIds, Map<Long, Integer> saleOrderFirstRowMap) {
            if (saleOrderIds.isEmpty()) {
                return;
            }

            List<Long> ids = new ArrayList<>(saleOrderIds);
            ids.sort(Long::compareTo);

            for (int i = 0; i < ids.size(); i += BATCH_SIZE) {
                int end = Math.min(i + BATCH_SIZE, ids.size());
                List<Long> batchIds = ids.subList(i, end);
                transactionTemplate.executeWithoutResult(status -> {
                    Map<Long, SaleOrder> saleOrderMap = saleOrderRepository.findAllById(batchIds)
                            .stream()
                            .collect(Collectors.toMap(SaleOrder::getId, saleOrder -> saleOrder));

                    if (saleOrderMap.size() != batchIds.size()) {
                        for (Long saleOrderId : batchIds) {
                            if (!saleOrderMap.containsKey(saleOrderId)) {
                                int rowNumber = saleOrderFirstRowMap.getOrDefault(saleOrderId, -1);
                                throw new ImportException(rowNumber, "销售订单",
                                        "销售订单不存在: " + saleOrderId);
                            }
                        }
                    }

                    Map<Long, Boolean> orderHasOpenItems = fetchOpenItemFlags(batchIds);

                    for (Long saleOrderId : batchIds) {
                        SaleOrder saleOrder = saleOrderMap.get(saleOrderId);
                        boolean hasOpenItems = orderHasOpenItems.getOrDefault(saleOrderId, Boolean.FALSE);
                        saleOrder.setStatus(hasOpenItems ? SaleOrderStatus.OPEN : SaleOrderStatus.CLOSED);
                    }

                    saleOrderRepository.saveAll(saleOrderMap.values());
                });
            }
        }

        private Map<Long, Boolean> fetchOpenItemFlags(List<Long> saleOrderIds) {
            List<Long> orderedIds = new ArrayList<>(saleOrderIds);
            orderedIds.sort(Long::compareTo);

            Map<Long, Boolean> result = new HashMap<>();
            List<Long> batchIds = new ArrayList<>(BATCH_SIZE);

            for (Long id : orderedIds) {
                batchIds.add(id);
                if (batchIds.size() >= BATCH_SIZE) {
                    queryOpenItems(batchIds, result);
                    batchIds.clear();
                }
            }
            if (!batchIds.isEmpty()) {
                queryOpenItems(batchIds, result);
            }
            return result;
        }

        private void queryOpenItems(List<Long> batchIds, Map<Long, Boolean> result) {
            List<Long> idsCopy = new ArrayList<>(batchIds);
            Map<Long, Boolean> flags = readOnlyTransactionTemplate.execute(status -> {
                Map<Long, Boolean> map = new HashMap<>();
                saleOrderItemRepository.findOpenFlagsBySaleOrderIds(idsCopy)
                        .forEach(flag -> map.put(flag.getSaleOrderId(), flag.isHasOpenItem()));
                return map;
            });
            if (flags != null) {
                result.putAll(flags);
            }
            for (Long id : batchIds) {
                result.putIfAbsent(id, Boolean.FALSE);
            }
        }

        private static final class ItemAggregation {
            private final SaleOrderItem saleOrderItem;
            private BigDecimal accumulatedQty = BigDecimal.ZERO;
            private BigDecimal remainingQty;

            private ItemAggregation(SaleOrderItem saleOrderItem) {
                this.saleOrderItem = saleOrderItem;
                this.remainingQty = saleOrderItem.getQty().subtract(saleOrderItem.getDeliveredQty());
            }

            private void addQuantity(BigDecimal qty, int rowNumber) {
                BigDecimal availableQty = remainingQty == null ? BigDecimal.ZERO : remainingQty;
                if (availableQty.compareTo(BigDecimal.ZERO) < 0) {
                    availableQty = BigDecimal.ZERO;
                }

                if (availableQty.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new ImportException(rowNumber, "实发数量",
                            String.format("销售订单剩余可出库数量不足，已出库数量（%s），销售数量（%s）",
                                    saleOrderItem.getDeliveredQty().toPlainString(),
                                    saleOrderItem.getQty().toPlainString()));
                }

                if (qty.compareTo(availableQty) > 0) {
                    BigDecimal totalDelivered = saleOrderItem.getDeliveredQty()
                            .add(accumulatedQty)
                            .add(qty);
                    throw new ImportException(rowNumber, "实发数量",
                            String.format("累计出库数量（%s）超过销售数量（%s）",
                                    totalDelivered.toPlainString(),
                                    saleOrderItem.getQty().toPlainString()));
                }

                accumulatedQty = accumulatedQty.add(qty);
                remainingQty = availableQty.subtract(qty);
            }

            private void apply() {
                if (accumulatedQty.compareTo(BigDecimal.ZERO) <= 0) {
                    return;
                }
                BigDecimal newDeliveredQty = saleOrderItem.getDeliveredQty().add(accumulatedQty);
                saleOrderItem.setDeliveredQty(newDeliveredQty);
                saleOrderItem.setStatus(newDeliveredQty.compareTo(saleOrderItem.getQty()) >= 0
                        ? SaleOrderItemStatus.CLOSED
                        : SaleOrderItemStatus.OPEN);
            }

            private SaleOrderItem saleOrderItem() {
                return saleOrderItem;
            }
        }

        private Set<String> findExistingBillNos(Set<String> billNos) {
            if (billNos.isEmpty()) {
                return Collections.emptySet();
            }
            List<String> billNoList = new ArrayList<>(billNos);
            List<List<String>> batches = new ArrayList<>();
            for (int i = 0; i < billNoList.size(); i += BATCH_SIZE) {
                int end = Math.min(i + BATCH_SIZE, billNoList.size());
                batches.add(new ArrayList<>(billNoList.subList(i, end)));
            }

            Set<String> existing = ConcurrentHashMap.newKeySet();
            List<Future<Void>> futures = new ArrayList<>();
            for (List<String> batch : batches) {
                futures.add(executorService.submit(() -> {
                    importConcurrencySemaphore.acquireUninterruptibly();
                    try {
                        List<String> found = readOnlyTransactionTemplate.execute(status ->
                                saleOutstockRepository.findExistingBillNos(batch));
                        if (found != null && !found.isEmpty()) {
                            existing.addAll(found);
                        }
                    } finally {
                        importConcurrencySemaphore.release();
                    }
                    return null;
                }));
            }
            for (Future<Void> future : futures) {
                try {
                    future.get();
                } catch (Exception e) {
                    futures.forEach(f -> f.cancel(false));
                    throw new RuntimeException("批量查询出库单编号失败: " + e.getMessage(), e);
                }
            }
            return existing;
        }

        private void addError(List<ImportError> errors, int rowNumber, String field, String message) {
            if (errors.size() < MAX_ERROR_COUNT) {
                errors.add(new ImportError(
                        "销售出库",
                        rowNumber,
                        field,
                        appendBillNo(rowNumber, message)
                ));
            }
        }

        private String appendBillNo(int rowNumber, String message) {
            String billNo = billNoByRowNumber.get(rowNumber);
            if (billNo == null || billNo.isEmpty()) {
                return message;
            }
            if (message.contains(billNo) || message.contains("出库单号")) {
                return message;
            }
            return message + "；出库单号: " + billNo;
        }

        private void validateMaterial(SaleOutstockItemData raw, SaleOrderItem saleOrderItem, int rowNumber) {
            String materialCode = trimToNull(raw.materialCode());
            if (materialCode != null && !materialCode.equalsIgnoreCase(saleOrderItem.getMaterial().getCode())) {
                throw new ImportException(rowNumber, "物料编码",
                        String.format("物料编码不匹配，Excel: %s，系统: %s",
                                materialCode, saleOrderItem.getMaterial().getCode()));
            }
        }

        private void validateUnit(SaleOutstockItemData raw, SaleOrderItem saleOrderItem, int rowNumber) {
            String unitCode = trimToNull(raw.unitCode());
            if (unitCode != null && !unitCode.equalsIgnoreCase(saleOrderItem.getUnit().getCode())) {
                throw new ImportException(rowNumber, "库存单位编码",
                        String.format("单位编码不匹配，Excel: %s，系统: %s",
                                unitCode, saleOrderItem.getUnit().getCode()));
            }
        }

        private LocalDate parseDate(String raw, int rowNumber) {
            try {
                String normalized = raw.replace("/", "-");
                return LocalDate.parse(normalized, DATE_FORMATTER);
            } catch (Exception e) {
                throw new ImportException(rowNumber, "出库日期", "日期格式错误: " + raw);
            }
        }

        private Integer parseInteger(String raw, int rowNumber, String field) {
            try {
                return Integer.parseInt(cleanNumericString(raw));
            } catch (NumberFormatException e) {
                throw new ImportException(rowNumber, field, "数值格式错误: " + raw);
            }
        }

        private BigDecimal parseDecimal(String raw, int rowNumber, String field) {
            try {
                return new BigDecimal(cleanNumericString(raw));
            } catch (NumberFormatException e) {
                throw new ImportException(rowNumber, field, "数值格式错误: " + raw);
            }
        }

        private String requireNonBlank(String value, int rowNumber, String field) {
            if (value == null || value.trim().isEmpty()) {
                throw new ImportException(rowNumber, field, field + "不能为空");
            }
            return value.trim();
        }

        private String trimToNull(String value) {
            if (value == null) {
                return null;
            }
            String trimmed = value.trim();
            return trimmed.isEmpty() ? null : trimmed;
        }

        private String cleanNumericString(String raw) {
            return raw.replace(",", "").replace("\"", "").trim();
        }

        private record SaleOutstockHeader(
                int rowNumber,
                String billNo,
                String outstockDate,
                String note
        ) {
        }

        private record SaleOutstockItemData(
                int rowNumber,
                String entrySequence,
                String materialCode,
                String materialName,
                String unitCode,
                String unitName,
                String realQty,
                String entryNote,
                String woNumber,
                String saleOrderEntryId
        ) {
        }

        private record SaleOutstockData(
                SaleOutstockHeader header,
                SaleOutstockItemData item
        ) {
        }

        private record ParsedItem(
                SaleOutstockItemData raw,
                Integer saleOrderSequence,
                Integer sequence,
                BigDecimal qty
        ) {
        }

        private record ImportTaskResult(
                Set<Long> affectedSaleOrderIds
        ) {
        }
    }

    private static class ImportException extends RuntimeException {
        private final int rowNumber;
        private final String field;

        private ImportException(int rowNumber, String field, String message) {
            super(message);
            this.rowNumber = rowNumber;
            this.field = field;
        }
    }
}

