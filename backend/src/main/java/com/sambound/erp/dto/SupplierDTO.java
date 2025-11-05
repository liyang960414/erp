package com.sambound.erp.dto;

import java.time.LocalDateTime;

public record SupplierDTO(
    Long id,
    String code,
    String name,
    String shortName,
    String englishName,
    String description,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}

