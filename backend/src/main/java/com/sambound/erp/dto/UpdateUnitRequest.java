package com.sambound.erp.dto;

import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record UpdateUnitRequest(
    @Size(max = 100, message = "单位名称长度不能超过100个字符")
    String name,
    
    Long unitGroupId,
    
    Boolean enabled,
    
    BigDecimal conversionNumerator,
    
    BigDecimal conversionDenominator
) {}

