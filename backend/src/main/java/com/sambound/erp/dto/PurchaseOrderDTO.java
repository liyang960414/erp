package com.sambound.erp.dto;

import com.sambound.erp.entity.PurchaseOrder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record PurchaseOrderDTO(
    Long id,
    String billNo,
    LocalDate orderDate,
    Long supplierId,
    String supplierCode,
    String supplierName,
    PurchaseOrder.OrderStatus status,
    String note,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    List<PurchaseOrderItemDTO> items
) {}

