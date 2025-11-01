package com.sambound.erp.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record UnitDTO(
    Long id,
    String code,
    String name,
    UnitGroupSummary unitGroup,
    Boolean enabled,
    BigDecimal conversionNumerator,
    BigDecimal conversionDenominator,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public record UnitGroupSummary(
        Long id,
        String code,
        String name
    ) {}
}

