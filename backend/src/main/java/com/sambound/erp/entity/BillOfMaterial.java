package com.sambound.erp.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * BOM（物料清单）头实体类
 */
@Entity
@Table(name = "bill_of_materials", 
       uniqueConstraints = {
           @UniqueConstraint(columnNames = {"material_id", "version"}, name = "uk_bom_material_version")
       })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BillOfMaterial {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "material_id", nullable = false)
    private Material material;

    @Column(nullable = false, length = 50)
    @Builder.Default
    private String version = "V000";

    @Column(length = 200)
    private String name;

    @Column(length = 100)
    private String category;

    @Column(length = 100)
    private String usage;

    @Column(columnDefinition = "TEXT")
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

