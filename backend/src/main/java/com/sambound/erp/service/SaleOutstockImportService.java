package com.sambound.erp.service;

import cn.idev.excel.FastExcel;
import cn.idev.excel.context.AnalysisContext;
import cn.idev.excel.read.listener.ReadListener;
import com.sambound.erp.dto.SaleOutstockExcelRow;
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
import com.sambound.erp.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class SaleOutstockImportService {

    private static final Logger logger = LoggerFactory.getLogger(SaleOutstockImportService.class);
    private static final int MAX_ERROR_COUNT = 1000;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final SaleOutstockRepository saleOutstockRepository;
    private final SaleOrderItemRepository saleOrderItemRepository;
    private final SaleOrderRepository saleOrderRepository;
    private final TransactionTemplate transactionTemplate;

    public SaleOutstockImportService(
            SaleOutstockRepository saleOutstockRepository,
            SaleOrderItemRepository saleOrderItemRepository,
            SaleOrderRepository saleOrderRepository,
            PlatformTransactionManager transactionManager) {
        this.saleOutstockRepository = saleOutstockRepository;
        this.saleOrderItemRepository = saleOrderItemRepository;
        this.saleOrderRepository = saleOrderRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
        this.transactionTemplate.setTimeout(120);
    }

    public SaleOutstockImportResponse importFromExcel(MultipartFile file) {
        logger.info("开始导入销售出库Excel文件: {}", file.getOriginalFilename());

        try {
            byte[] fileBytes = file.getBytes();

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

            int totalOutstockCount = outstockGroups.size();
            logger.info("找到 {} 张销售出库单，{} 条明细，开始导入数据库", totalOutstockCount, outstockDataList.size());

            List<SaleOutstockImportResponse.ImportError> errors = Collections.synchronizedList(new ArrayList<>());
            AtomicInteger successCount = new AtomicInteger(0);

            outstockGroups.forEach((header, items) -> {
                if (errors.size() >= MAX_ERROR_COUNT) {
                    return;
                }

                try {
                    transactionTemplate.execute(status -> {
                        processOutstock(header, items, errors);
                        return null;
                    });
                    successCount.incrementAndGet();
                } catch (ImportException e) {
                    if (errors.size() < MAX_ERROR_COUNT) {
                        errors.add(new SaleOutstockImportResponse.ImportError(
                                "销售出库",
                                e.rowNumber,
                                e.field,
                                e.getMessage()
                        ));
                    }
                } catch (Exception e) {
                    logger.error("导入销售出库单失败，单据编号: {}", header.billNo(), e);
                    if (errors.size() < MAX_ERROR_COUNT) {
                        errors.add(new SaleOutstockImportResponse.ImportError(
                                "销售出库",
                                header.rowNumber(),
                                "单据编号",
                                "导入失败: " + e.getMessage()
                        ));
                    }
                }
            });

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

        private void processOutstock(
                SaleOutstockHeader header,
                List<SaleOutstockItemData> items,
                List<SaleOutstockImportResponse.ImportError> errors) {

            String billNo = requireNonBlank(header.billNo(), header.rowNumber(), "单据编号");

            Optional<SaleOutstock> existing = saleOutstockRepository.findByBillNo(billNo);
            if (existing.isPresent()) {
                throw new ImportException(header.rowNumber(), "单据编号",
                        "销售出库单已存在: " + billNo);
            }

            LocalDate outstockDate = parseDate(requireNonBlank(header.outstockDate(), header.rowNumber(), "出库日期"), header.rowNumber());
            String note = trimToNull(header.note());

            if (items.isEmpty()) {
                throw new ImportException(header.rowNumber(), "明细",
                        "销售出库单没有任何明细记录");
            }

            List<Integer> saleOrderSequences = new ArrayList<>();
            List<ParsedItem> parsedItems = new ArrayList<>();

            for (SaleOutstockItemData item : items) {
                int rowNumber = item.rowNumber();
                Integer saleOrderSequence = parseInteger(requireNonBlank(item.saleOrderEntryId(), rowNumber, "销售订单EntryId"), rowNumber, "销售订单EntryId");
                Integer sequence = parseInteger(requireNonBlank(item.entrySequence(), rowNumber, "明细序号"), rowNumber, "明细序号");
                BigDecimal qty = parseDecimal(requireNonBlank(item.realQty(), rowNumber, "实发数量"), rowNumber, "实发数量");

                saleOrderSequences.add(saleOrderSequence);
                parsedItems.add(new ParsedItem(item, saleOrderSequence, sequence, qty));
            }

            Map<Integer, List<SaleOrderItem>> sequenceToItems = new LinkedHashMap<>();
            if (!saleOrderSequences.isEmpty()) {
                saleOrderItemRepository.findBySequenceIn(saleOrderSequences).forEach(item -> {
                    sequenceToItems.computeIfAbsent(item.getSequence(), k -> new ArrayList<>()).add(item);
                });
            }

            SaleOutstock outstock = SaleOutstock.builder()
                    .billNo(billNo)
                    .outstockDate(outstockDate)
                    .note(note)
                    .build();

            List<SaleOrderItem> itemsToUpdate = new ArrayList<>();
            Set<Long> affectedSaleOrderIds = new HashSet<>();

            for (ParsedItem parsed : parsedItems) {
                List<SaleOrderItem> candidates = sequenceToItems.get(parsed.saleOrderSequence());
                if (candidates == null || candidates.isEmpty()) {
                    throw new ImportException(parsed.raw().rowNumber(), "销售订单EntryId",
                            "无法找到对应的销售订单明细，序号: " + parsed.raw().saleOrderEntryId());
                }
                if (candidates.size() > 1) {
                    throw new ImportException(parsed.raw().rowNumber(), "销售订单EntryId",
                            "存在多个销售订单明细匹配序号 " + parsed.raw().saleOrderEntryId() + "，请确认数据唯一性");
                }
                SaleOrderItem saleOrderItem = candidates.get(0);

                validateMaterial(parsed, saleOrderItem);
                validateUnit(parsed, saleOrderItem);

                BigDecimal newDeliveredQty = saleOrderItem.getDeliveredQty().add(parsed.qty());
                if (newDeliveredQty.compareTo(saleOrderItem.getQty()) > 0) {
                    throw new ImportException(parsed.raw().rowNumber(), "实发数量",
                            String.format("累计出库数量（%s）超过销售数量（%s）",
                                    newDeliveredQty.toPlainString(),
                                    saleOrderItem.getQty().toPlainString()));
                }

                SaleOutstockItem outstockItem = SaleOutstockItem.builder()
                        .sequence(parsed.sequence())
                        .saleOrderItem(saleOrderItem)
                        .saleOrderSequence(parsed.saleOrderSequence())
                        .material(saleOrderItem.getMaterial())
                        .unit(saleOrderItem.getUnit())
                        .qty(parsed.qty())
                        .entryNote(trimToNull(parsed.raw().entryNote()))
                        .woNumber(trimToNull(parsed.raw().woNumber()))
                        .build();

                outstock.addItem(outstockItem);

                saleOrderItem.setDeliveredQty(newDeliveredQty);
                saleOrderItem.setStatus(newDeliveredQty.compareTo(saleOrderItem.getQty()) >= 0
                        ? SaleOrderItemStatus.CLOSED
                        : SaleOrderItemStatus.OPEN);

                itemsToUpdate.add(saleOrderItem);
                affectedSaleOrderIds.add(saleOrderItem.getSaleOrder().getId());
            }

            saleOutstockRepository.save(outstock);
            saleOrderItemRepository.saveAll(itemsToUpdate);

            for (Long saleOrderId : affectedSaleOrderIds) {
                boolean hasOpenItems = saleOrderItemRepository.existsBySaleOrderIdAndStatus(saleOrderId, SaleOrderItemStatus.OPEN);
                SaleOrder saleOrder = saleOrderRepository.findById(saleOrderId)
                        .orElseThrow(() -> new BusinessException("销售订单不存在: " + saleOrderId));
                saleOrder.setStatus(hasOpenItems ? SaleOrderStatus.OPEN : SaleOrderStatus.CLOSED);
                saleOrderRepository.save(saleOrder);
            }
        }

        private void validateMaterial(ParsedItem parsed, SaleOrderItem saleOrderItem) {
            String materialCode = trimToNull(parsed.raw().materialCode());
            if (materialCode != null && !materialCode.equalsIgnoreCase(saleOrderItem.getMaterial().getCode())) {
                throw new ImportException(parsed.raw().rowNumber(), "物料编码",
                        String.format("物料编码不匹配，Excel: %s，系统: %s",
                                materialCode, saleOrderItem.getMaterial().getCode()));
            }
        }

        private void validateUnit(ParsedItem parsed, SaleOrderItem saleOrderItem) {
            String unitCode = trimToNull(parsed.raw().unitCode());
            if (unitCode != null && !unitCode.equalsIgnoreCase(saleOrderItem.getUnit().getCode())) {
                throw new ImportException(parsed.raw().rowNumber(), "库存单位编码",
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

