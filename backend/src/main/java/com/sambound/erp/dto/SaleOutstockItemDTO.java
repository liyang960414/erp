package com.sambound.erp.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SaleOutstockItemDTO(
    Long id,
    Long saleOutstockId,
    Integer sequence,
    Long saleOrderId,
    String saleOrderBillNo,
    Long saleOrderItemId,
    Integer saleOrderItemSequence,
    Integer saleOrderSequence,
    String materialCode,
    String materialName,
    String unitCode,
    String unitName,
    BigDecimal qty,
    String entryNote,
    String woNumber,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}

