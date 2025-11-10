package com.sambound.erp.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.sambound.erp.enums.SaleOrderStatus;

public record SaleOrderDTO(
    Long id,
    String billNo,
    LocalDate orderDate,
    String note,
    String woNumber,
    Long customerId,
    String customerCode,
    String customerName,
    SaleOrderStatus status,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    List<SaleOrderItemDTO> items
) {}
