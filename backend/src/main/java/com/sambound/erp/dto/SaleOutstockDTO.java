package com.sambound.erp.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record SaleOutstockDTO(
    Long id,
    String billNo,
    LocalDate outstockDate,
    String note,
    Integer itemCount,
    BigDecimal totalQty,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    List<SaleOutstockItemDTO> items
) {}

