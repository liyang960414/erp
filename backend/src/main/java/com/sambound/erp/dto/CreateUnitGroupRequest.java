package com.sambound.erp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateUnitGroupRequest(
    @NotBlank(message = "单位组编码不能为空")
    @Size(max = 50, message = "单位组编码长度不能超过50个字符")
    String code,
    
    @NotBlank(message = "单位组名称不能为空")
    @Size(max = 100, message = "单位组名称长度不能超过100个字符")
    String name,
    
    @Size(max = 200, message = "描述长度不能超过200个字符")
    String description
) {}

