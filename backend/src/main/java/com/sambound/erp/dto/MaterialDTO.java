package com.sambound.erp.dto;

import java.time.LocalDateTime;

public record MaterialDTO(
    Long id,
    String code,
    String name,
    String specification,
    String mnemonicCode,
    String oldNumber,
    String description,
    Long materialGroupId,
    String materialGroupCode,
    String materialGroupName,
    Long baseUnitId,
    String baseUnitCode,
    String baseUnitName,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}

