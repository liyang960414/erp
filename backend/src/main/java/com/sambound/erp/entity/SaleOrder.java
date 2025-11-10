package com.sambound.erp.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.sambound.erp.enums.SaleOrderStatus;

/**
 * 销售订单实体类
 */
@Entity
@Table(name = "sale_orders")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaleOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bill_no", unique = true, nullable = false, length = 100)
    private String billNo;

    @Column(name = "order_date", nullable = false)
    private LocalDate orderDate;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(name = "wo_number", length = 100)
    private String woNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private SaleOrderStatus status = SaleOrderStatus.OPEN;

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
