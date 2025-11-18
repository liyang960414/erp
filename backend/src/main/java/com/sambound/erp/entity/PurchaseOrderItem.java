package com.sambound.erp.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 采购订单明细实体类
 */
@Entity
@Table(name = "purchase_order_items")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchaseOrderItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_order_id", nullable = false)
    private PurchaseOrder purchaseOrder;

    @Column(nullable = false)
    private Integer sequence;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "material_id", nullable = false)
    private Material material;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bom_id")
    private BillOfMaterial bom;

    @Column(name = "material_desc", columnDefinition = "TEXT")
    private String materialDesc;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_id", nullable = false)
    private Unit unit;

    @Column(nullable = false, precision = 18, scale = 6)
    private BigDecimal qty;

    @Column(name = "plan_confirm")
    @Builder.Default
    private Boolean planConfirm = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sal_unit_id")
    private Unit salUnit;

    @Column(name = "sal_qty", precision = 18, scale = 6)
    private BigDecimal salQty;

    @Column(name = "sal_join_qty", precision = 18, scale = 6)
    private BigDecimal salJoinQty;

    @Column(name = "base_sal_join_qty", precision = 18, scale = 6)
    private BigDecimal baseSalJoinQty;

    @Column(columnDefinition = "TEXT")
    private String remarks;

    @Column(name = "sal_base_qty", precision = 18, scale = 6)
    private BigDecimal salBaseQty;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sub_req_order_item_id")
    private SubReqOrderItem subReqOrderItem;

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

