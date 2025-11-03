package com.sambound.erp.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * BOM（物料清单）明细实体类
 */
@Entity
@Table(name = "bom_items")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BomItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bom_id", nullable = false)
    private BillOfMaterial bom;

    @Column(nullable = false)
    private Integer sequence;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "child_material_id", nullable = false)
    private Material childMaterial;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "child_unit_id", nullable = false)
    private Unit childUnit;

    @Column(nullable = false, precision = 18, scale = 6)
    @Builder.Default
    private BigDecimal numerator = BigDecimal.ONE;

    @Column(nullable = false, precision = 18, scale = 6)
    @Builder.Default
    private BigDecimal denominator = BigDecimal.ONE;

    @Column(name = "scrap_rate", precision = 5, scale = 2)
    private BigDecimal scrapRate;

    @Column(name = "child_bom_version", length = 50)
    private String childBomVersion;

    @Column(columnDefinition = "TEXT")
    private String memo;

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

