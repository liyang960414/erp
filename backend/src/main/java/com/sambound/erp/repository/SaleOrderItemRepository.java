package com.sambound.erp.repository;

import com.sambound.erp.entity.SaleOrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SaleOrderItemRepository extends JpaRepository<SaleOrderItem, Long> {
    /**
     * 根据订单ID查询所有明细,包含物料和单位信息
     */
    @Query("SELECT soi FROM SaleOrderItem soi " +
           "LEFT JOIN FETCH soi.material " +
           "LEFT JOIN FETCH soi.unit " +
           "WHERE soi.saleOrder.id = :saleOrderId " +
           "ORDER BY soi.sequence")
    List<SaleOrderItem> findBySaleOrderId(@Param("saleOrderId") Long saleOrderId);
    
    /**
     * 查询所有订单明细用于提醒检查,包含订单、物料、单位信息
     */
    @Query("SELECT soi FROM SaleOrderItem soi " +
           "LEFT JOIN FETCH soi.saleOrder so " +
           "LEFT JOIN FETCH so.customer " +
           "LEFT JOIN FETCH soi.material " +
           "LEFT JOIN FETCH soi.unit " +
           "WHERE soi.inspectionDate IS NOT NULL OR soi.deliveryDate IS NOT NULL")
    List<SaleOrderItem> findAllForAlerts();
}
