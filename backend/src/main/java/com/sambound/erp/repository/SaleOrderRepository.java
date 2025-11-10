package com.sambound.erp.repository;

import com.sambound.erp.entity.SaleOrder;
import com.sambound.erp.enums.SaleOrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface SaleOrderRepository extends JpaRepository<SaleOrder, Long> {
    Optional<SaleOrder> findByBillNo(String billNo);
    boolean existsByBillNo(String billNo);
    
    /**
     * 分页查询销售订单,包含客户信息
     */
    @Query("SELECT DISTINCT so FROM SaleOrder so " +
           "LEFT JOIN FETCH so.customer c " +
           "WHERE (:billNo IS NULL OR so.billNo LIKE :billNo) " +
           "AND (:customerCode IS NULL OR c.code LIKE :customerCode) " +
           "AND (:customerName IS NULL OR c.name LIKE :customerName) " +
           "AND (:status IS NULL OR so.status = :status) " +
           "AND (:woNumber IS NULL OR so.woNumber LIKE :woNumber) " +
           "AND (:startDate IS NULL OR so.orderDate >= :startDate) " +
           "AND (:endDate IS NULL OR so.orderDate <= :endDate)")
    Page<SaleOrder> findByConditions(
        @Param("billNo") String billNo,
        @Param("customerCode") String customerCode,
        @Param("customerName") String customerName,
        @Param("status") SaleOrderStatus status,
        @Param("woNumber") String woNumber,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate,
        Pageable pageable
    );
    
    /**
     * 根据ID查询订单详情,包含客户信息
     */
    @Query("SELECT so FROM SaleOrder so " +
           "LEFT JOIN FETCH so.customer " +
           "WHERE so.id = :id")
    Optional<SaleOrder> findByIdWithCustomer(@Param("id") Long id);
}
