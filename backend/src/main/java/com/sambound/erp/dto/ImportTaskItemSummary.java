package com.sambound.erp.dto;

import java.time.LocalDateTime;

public record ImportTaskItemSummary(
        Long itemId,
        Integer sequenceNo,
        String status,
        String fileName,
        Integer totalCount,
        Integer successCount,
        Integer failureCount,
        String failureReason,
        LocalDateTime createdAt,
        LocalDateTime startedAt,
        LocalDateTime completedAt
) {
}


