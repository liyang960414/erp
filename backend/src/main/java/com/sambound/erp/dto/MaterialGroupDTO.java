package com.sambound.erp.dto;

import java.time.LocalDateTime;

public record MaterialGroupDTO(
    Long id,
    String code,
    String name,
    String description,
    Long parentId,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}

