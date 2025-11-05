package com.sambound.erp.dto;

import java.time.LocalDateTime;

public record CustomerDTO(
    Long id,
    String code,
    String name,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
