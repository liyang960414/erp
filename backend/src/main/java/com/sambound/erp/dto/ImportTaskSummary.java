package com.sambound.erp.dto;

import java.time.LocalDateTime;

public record ImportTaskSummary(
        Long taskId,
        String taskCode,
        String importType,
        String status,
        String fileName,
        String createdBy,
        Integer totalCount,
        Integer successCount,
        Integer failureCount,
        LocalDateTime createdAt,
        LocalDateTime startedAt,
        LocalDateTime completedAt
) {
}


