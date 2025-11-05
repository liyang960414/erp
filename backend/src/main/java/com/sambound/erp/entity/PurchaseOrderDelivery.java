package com.sambound.erp.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 采购订单交货明细实体类
 */
@Entity
@Table(name = "purchase_order_deliveries")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchaseOrderDelivery {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_order_item_id", nullable = false)
    private PurchaseOrderItem purchaseOrderItem;

    @Column(nullable = false)
    private Integer sequence;

    @Column(name = "delivery_date", nullable = false)
    private LocalDate deliveryDate;

    @Column(name = "plan_qty", nullable = false, precision = 18, scale = 6)
    private BigDecimal planQty;

    @Column(name = "supplier_delivery_date")
    private LocalDate supplierDeliveryDate;

    @Column(name = "pre_arrival_date")
    private LocalDate preArrivalDate;

    @Column(name = "transport_lead_time")
    private Integer transportLeadTime;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

