package com.sambound.erp.dto;

import java.math.BigDecimal;
import java.util.List;

public record UpdateBomRequest(
    String name,
    String category,
    String usage,
    String description,
    
    List<UpdateBomItemRequest> items
) {
    public record UpdateBomItemRequest(
        Long id,
        Integer sequence,
        Long childMaterialId,
        Long childUnitId,
        BigDecimal numerator,
        BigDecimal denominator,
        BigDecimal scrapRate,
        String childBomVersion,
        String memo
    ) {}
}

