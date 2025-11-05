package com.sambound.erp.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record PurchaseOrderDeliveryDTO(
    Long id,
    Long purchaseOrderItemId,
    Integer sequence,
    LocalDate deliveryDate,
    BigDecimal planQty,
    LocalDate supplierDeliveryDate,
    LocalDate preArrivalDate,
    Integer transportLeadTime,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}

