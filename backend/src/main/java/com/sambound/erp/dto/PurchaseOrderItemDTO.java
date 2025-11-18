package com.sambound.erp.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PurchaseOrderItemDTO(
    Long id,
    Long purchaseOrderId,
    Integer sequence,
    Long materialId,
    String materialCode,
    String materialName,
    Long bomId,
    String bomVersion,
    String materialDesc,
    Long unitId,
    String unitCode,
    String unitName,
    BigDecimal qty,
    Boolean planConfirm,
    Long salUnitId,
    String salUnitCode,
    String salUnitName,
    BigDecimal salQty,
    BigDecimal salJoinQty,
    BigDecimal baseSalJoinQty,
    String remarks,
    BigDecimal salBaseQty,
    BigDecimal deliveredQty,  // 已交货数量汇总
    SubReqOrderItemSummary subReqOrderItem,  // 关联的委外订单明细
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public record SubReqOrderItemSummary(
        Long id,
        Integer sequence,
        Long subReqOrderId,
        Integer subReqOrderBillHeadSeq
    ) {}
}

