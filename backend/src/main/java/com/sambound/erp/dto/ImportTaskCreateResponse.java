package com.sambound.erp.dto;

import java.time.LocalDateTime;

public record ImportTaskCreateResponse(
        Long taskId,
        String taskCode,
        String importType,
        String status,
        String fileName,
        String createdBy,
        LocalDateTime createdAt
) {
}


