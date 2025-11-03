package com.sambound.erp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

public record CreateBomRequest(
    @NotNull(message = "父项物料ID不能为空")
    Long materialId,
    
    @NotBlank(message = "BOM版本不能为空")
    String version,
    
    String name,
    String category,
    String usage,
    String description,
    
    List<CreateBomItemRequest> items
) {
    public record CreateBomItemRequest(
        @NotNull(message = "序号不能为空")
        Integer sequence,
        
        @NotNull(message = "子项物料ID不能为空")
        Long childMaterialId,
        
        @NotNull(message = "子项单位ID不能为空")
        Long childUnitId,
        
        @NotNull(message = "用量分子不能为空")
        BigDecimal numerator,
        
        @NotNull(message = "用量分母不能为空")
        BigDecimal denominator,
        
        BigDecimal scrapRate,
        String childBomVersion,
        String memo
    ) {}
}

