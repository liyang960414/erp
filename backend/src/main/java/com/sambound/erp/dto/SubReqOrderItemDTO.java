package com.sambound.erp.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SubReqOrderItemDTO(
    Long id,
    Long subReqOrderId,
    Integer sequence,
    Long materialId,
    String materialCode,
    String materialName,
    Long unitId,
    String unitCode,
    String unitName,
    BigDecimal qty,
    Long bomId,
    String bomVersion,
    String bomVersionName,
    Long supplierId,
    String supplierCode,
    String supplierName,
    String lotMaster,
    String lotManual,
    BigDecimal baseNoStockInQty,
    BigDecimal noStockInQty,
    String pickMtrlStatus,
    String description,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}

