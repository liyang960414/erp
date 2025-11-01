package com.sambound.erp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateUnitRequest(
    @NotBlank(message = "单位编码不能为空")
    @Size(max = 50, message = "单位编码长度不能超过50个字符")
    String code,
    
    @NotBlank(message = "单位名称不能为空")
    @Size(max = 100, message = "单位名称长度不能超过100个字符")
    String name,
    
    @NotNull(message = "单位组ID不能为空")
    Long unitGroupId,
    
    Boolean enabled
) {}

