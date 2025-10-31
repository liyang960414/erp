package com.sambound.erp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, message = "密码长度至少为6个字符")
    String newPassword
) {}

