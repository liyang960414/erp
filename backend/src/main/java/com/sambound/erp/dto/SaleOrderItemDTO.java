package com.sambound.erp.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record SaleOrderItemDTO(
    Long id,
    Long saleOrderId,
    Integer sequence,
    Long materialId,
    String materialCode,
    String materialName,
    Long unitId,
    String unitCode,
    String unitName,
    BigDecimal qty,
    BigDecimal oldQty,
    LocalDate inspectionDate,
    LocalDateTime deliveryDate,
    String bomVersion,
    String entryNote,
    String customerOrderNo,
    String customerLineNo,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
