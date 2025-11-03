package com.sambound.erp.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record BomItemDTO(
    Long id,
    Long bomId,
    Integer sequence,
    Long childMaterialId,
    String childMaterialCode,
    String childMaterialName,
    Long childUnitId,
    String childUnitCode,
    String childUnitName,
    BigDecimal numerator,
    BigDecimal denominator,
    BigDecimal scrapRate,
    String childBomVersion,
    String memo,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}

