package com.sambound.erp.dto;

import java.time.LocalDateTime;

public record ImportTaskFailureDTO(
        Long id,
        String section,
        Integer rowNumber,
        String field,
        String message,
        String status,
        String rawPayload,
        LocalDateTime createdAt,
        LocalDateTime resolvedAt
) {
}


