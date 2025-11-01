package com.sambound.erp.dto;

import java.time.LocalDateTime;

public record UnitGroupDTO(
    Long id,
    String code,
    String name,
    String description,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}

