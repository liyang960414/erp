package com.sambound.erp.dto;

import java.time.LocalDateTime;

/**
 * 审计日志响应DTO
 */
public record AuditLogResponse(
        Long id,
        String username,
        Long userId,
        String action,
        String module,
        String resourceType,
        String resourceId,
        String description,
        String requestMethod,
        String requestUri,
        String ipAddress,
        String status,
        String errorMessage,
        LocalDateTime createdAt
) {
}

