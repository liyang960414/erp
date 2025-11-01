package com.sambound.erp.dto;

import jakarta.validation.constraints.Size;

public record UpdateUnitGroupRequest(
    @Size(max = 100, message = "单位组名称长度不能超过100个字符")
    String name,
    
    @Size(max = 200, message = "描述长度不能超过200个字符")
    String description
) {}

