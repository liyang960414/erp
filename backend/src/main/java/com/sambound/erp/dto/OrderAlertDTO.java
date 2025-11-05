package com.sambound.erp.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 订单提醒DTO
 * 用于首页显示订单提醒信息
 */
public record OrderAlertDTO(
    /**
     * 提醒类型
     * PURCHASE_REMINDER: 采购料提醒（验货期前40天）
     * PRODUCTION_REMINDER: 生产提醒（临近验货期7天内）
     * DELIVERY_OVERDUE: 要货日期超期告警
     */
    AlertType alertType,
    
    /**
     * 订单基本信息
     */
    Long orderId,
    String billNo,
    String customerName,
    String woNumber,
    
    /**
     * 订单明细信息
     */
    Long orderItemId,
    String materialCode,
    String materialName,
    BigDecimal qty,
    String unitCode,
    String unitName,
    
    /**
     * 关键日期
     */
    LocalDate inspectionDate,
    LocalDateTime deliveryDate,
    
    /**
     * 提醒相关信息
     */
    Long daysRemaining,  // 剩余天数（正数）或超期天数（负数）
    String alertMessage  // 提醒消息
) {
    /**
     * 提醒类型枚举
     */
    public enum AlertType {
        PURCHASE_REMINDER,    // 采购料提醒
        PRODUCTION_REMINDER,  // 生产提醒
        DELIVERY_OVERDUE      // 要货日期超期告警
    }
}


