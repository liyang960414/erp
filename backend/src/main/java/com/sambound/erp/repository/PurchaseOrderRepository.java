package com.sambound.erp.repository;

import com.sambound.erp.entity.PurchaseOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {
    Optional<PurchaseOrder> findByBillNo(String billNo);
    boolean existsByBillNo(String billNo);
    
    /**
     * 分页查询采购订单,包含供应商信息
     */
    @Query("SELECT po FROM PurchaseOrder po " +
           "LEFT JOIN FETCH po.supplier " +
           "WHERE (:billNo IS NULL OR po.billNo LIKE :billNo) " +
           "AND (:supplierCode IS NULL OR po.supplier.code LIKE :supplierCode) " +
           "AND (:status IS NULL OR po.status = :status) " +
           "AND (:startDate IS NULL OR po.orderDate >= :startDate) " +
           "AND (:endDate IS NULL OR po.orderDate <= :endDate)")
    Page<PurchaseOrder> findByConditions(
        @Param("billNo") String billNo,
        @Param("supplierCode") String supplierCode,
        @Param("status") PurchaseOrder.OrderStatus status,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate,
        Pageable pageable
    );
    
    /**
     * 根据ID查询订单详情,包含供应商信息
     */
    @Query("SELECT po FROM PurchaseOrder po " +
           "LEFT JOIN FETCH po.supplier " +
           "WHERE po.id = :id")
    Optional<PurchaseOrder> findByIdWithSupplier(@Param("id") Long id);
}

