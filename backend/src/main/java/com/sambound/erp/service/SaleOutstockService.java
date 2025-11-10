package com.sambound.erp.service;

import com.sambound.erp.dto.SaleOutstockDTO;
import com.sambound.erp.dto.SaleOutstockItemDTO;
import com.sambound.erp.entity.SaleOutstock;
import com.sambound.erp.entity.SaleOutstockItem;
import com.sambound.erp.entity.SaleOrder;
import com.sambound.erp.entity.SaleOrderItem;
import com.sambound.erp.exception.BusinessException;
import com.sambound.erp.repository.SaleOutstockItemRepository;
import com.sambound.erp.repository.SaleOutstockRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class SaleOutstockService {

    private final SaleOutstockRepository saleOutstockRepository;
    private final SaleOutstockItemRepository saleOutstockItemRepository;

    public SaleOutstockService(
            SaleOutstockRepository saleOutstockRepository,
            SaleOutstockItemRepository saleOutstockItemRepository) {
        this.saleOutstockRepository = saleOutstockRepository;
        this.saleOutstockItemRepository = saleOutstockItemRepository;
    }

    public Page<SaleOutstockDTO> getSaleOutstocks(
            String billNo,
            LocalDate startDate,
            LocalDate endDate,
            Pageable pageable) {

        String billNoPattern = billNo != null && !billNo.trim().isEmpty()
                ? "%" + billNo.trim().toLowerCase() + "%" : null;

        Page<SaleOutstock> outstocks = saleOutstockRepository.findByConditions(
                billNoPattern, startDate, endDate, pageable);

        List<Long> outstockIds = outstocks.stream()
                .map(SaleOutstock::getId)
                .toList();

        Map<Long, Summary> summaryMap = outstockIds.isEmpty()
                ? Map.of()
                : saleOutstockItemRepository
                        .findSummaryBySaleOutstockIds(outstockIds)
                        .stream()
                        .collect(Collectors.toMap(
                                row -> ((Number) row[0]).longValue(),
                                row -> new Summary(
                                        ((Number) row[1]).intValue(),
                                        (BigDecimal) row[2]
                                )
                        ));

        return outstocks.map(outstock -> {
            Summary summary = summaryMap.getOrDefault(outstock.getId(), new Summary(0, BigDecimal.ZERO));
            return toDTO(outstock, summary.itemCount(), summary.totalQty(), Collections.emptyList());
        });
    }

    public SaleOutstockDTO getSaleOutstockById(Long id) {
        SaleOutstock outstock = saleOutstockRepository.findById(id)
                .orElseThrow(() -> new BusinessException("销售出库单不存在"));

        List<SaleOutstockItem> items = saleOutstockItemRepository.findDetailedBySaleOutstockId(id);
        Summary summary = new Summary(items.size(),
                items.stream()
                        .map(SaleOutstockItem::getQty)
                        .reduce(BigDecimal.ZERO, BigDecimal::add));

        List<SaleOutstockItemDTO> itemDTOs = items.stream()
                .map(this::toItemDTO)
                .toList();

        return toDTO(outstock, summary.itemCount(), summary.totalQty(), itemDTOs);
    }

    private SaleOutstockDTO toDTO(SaleOutstock outstock, int itemCount, BigDecimal totalQty, List<SaleOutstockItemDTO> items) {
        return new SaleOutstockDTO(
                outstock.getId(),
                outstock.getBillNo(),
                outstock.getOutstockDate(),
                outstock.getNote(),
                itemCount,
                totalQty,
                outstock.getCreatedAt(),
                outstock.getUpdatedAt(),
                items.isEmpty() ? null : items
        );
    }

    private SaleOutstockItemDTO toItemDTO(SaleOutstockItem item) {
        SaleOrderItem saleOrderItem = item.getSaleOrderItem();
        SaleOrder saleOrder = saleOrderItem != null ? saleOrderItem.getSaleOrder() : null;

        return new SaleOutstockItemDTO(
                item.getId(),
                item.getSaleOutstock() != null ? item.getSaleOutstock().getId() : null,
                item.getSequence(),
                saleOrder != null ? saleOrder.getId() : null,
                saleOrder != null ? saleOrder.getBillNo() : null,
                saleOrderItem != null ? saleOrderItem.getId() : null,
                saleOrderItem != null ? saleOrderItem.getSequence() : null,
                item.getSaleOrderSequence(),
                item.getMaterial() != null ? item.getMaterial().getCode() : null,
                item.getMaterial() != null ? item.getMaterial().getName() : null,
                item.getUnit() != null ? item.getUnit().getCode() : null,
                item.getUnit() != null ? item.getUnit().getName() : null,
                item.getQty(),
                item.getEntryNote(),
                item.getWoNumber(),
                item.getCreatedAt(),
                item.getUpdatedAt()
        );
    }

    private record Summary(int itemCount, BigDecimal totalQty) {}
}

