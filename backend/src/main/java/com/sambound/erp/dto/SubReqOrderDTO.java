package com.sambound.erp.dto;

import com.sambound.erp.entity.SubReqOrder;

import java.time.LocalDateTime;
import java.util.List;

public record SubReqOrderDTO(
    Long id,
    Integer billHeadSeq,
    String description,
    SubReqOrder.OrderStatus status,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    List<SubReqOrderItemDTO> items
) {}

