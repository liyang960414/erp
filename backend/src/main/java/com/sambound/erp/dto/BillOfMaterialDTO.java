package com.sambound.erp.dto;

import java.time.LocalDateTime;
import java.util.List;

public record BillOfMaterialDTO(
    Long id,
    Long materialId,
    String materialCode,
    String materialName,
    String materialGroupCode,
    String materialGroupName,
    String version,
    String name,
    String category,
    String usage,
    String description,
    List<BomItemDTO> items,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}

