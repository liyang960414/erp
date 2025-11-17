package com.sambound.erp.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 委外订单明细实体类
 */
@Entity
@Table(name = "sub_req_order_items")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubReqOrderItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sub_req_order_id", nullable = false)
    private SubReqOrder subReqOrder;

    @Column(nullable = false)
    private Integer sequence;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "material_id", nullable = false)
    private Material material;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_id", nullable = false)
    private Unit unit;

    @Column(nullable = false, precision = 18, scale = 6)
    private BigDecimal qty;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bom_id")
    private BillOfMaterial bom;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    @Column(name = "lot_master", length = 200)
    private String lotMaster;

    @Column(name = "lot_manual", length = 200)
    private String lotManual;

    @Column(name = "base_no_stock_in_qty", precision = 18, scale = 6)
    private BigDecimal baseNoStockInQty;

    @Column(name = "no_stock_in_qty", precision = 18, scale = 6)
    private BigDecimal noStockInQty;

    @Column(name = "pick_mtrl_status", length = 50)
    private String pickMtrlStatus;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

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

