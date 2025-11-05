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
 * 销售订单明细实体类
 */
@Entity
@Table(name = "sale_order_items")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaleOrderItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sale_order_id", nullable = false)
    private SaleOrder saleOrder;

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

    @Column(name = "old_qty", precision = 18, scale = 6)
    private BigDecimal oldQty;

    @Column(name = "inspection_date")
    private LocalDate inspectionDate;

    @Column(name = "delivery_date")
    private LocalDateTime deliveryDate;

    @Column(name = "bom_version", length = 50)
    private String bomVersion;

    @Column(name = "entry_note", columnDefinition = "TEXT")
    private String entryNote;

    @Column(name = "customer_order_no", length = 100)
    private String customerOrderNo;

    @Column(name = "customer_line_no", length = 50)
    private String customerLineNo;

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
