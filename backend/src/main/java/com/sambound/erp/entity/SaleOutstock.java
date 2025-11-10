package com.sambound.erp.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "sale_outstocks")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaleOutstock {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bill_no", nullable = false, unique = true, length = 100)
    private String billNo;

    @Column(name = "outstock_date", nullable = false)
    private LocalDate outstockDate;

    @Column(columnDefinition = "TEXT")
    private String note;

    @OneToMany(mappedBy = "saleOutstock", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<SaleOutstockItem> items = new ArrayList<>();

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

    public void addItem(SaleOutstockItem item) {
        items.add(item);
        item.setSaleOutstock(this);
    }
}

