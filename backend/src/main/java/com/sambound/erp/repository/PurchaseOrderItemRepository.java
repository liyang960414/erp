package com.sambound.erp.repository;

import com.sambound.erp.entity.PurchaseOrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PurchaseOrderItemRepository extends JpaRepository<PurchaseOrderItem, Long> {
    
    /**
     * 根据采购订单ID查询所有明细
     */
    List<PurchaseOrderItem> findByPurchaseOrderId(Long purchaseOrderId);
    
    /**
     * 根据采购订单ID查询所有明细，包含关联的物料、单位、BOM信息和委外订单明细
     */
    @Query("SELECT DISTINCT poi FROM PurchaseOrderItem poi " +
           "LEFT JOIN FETCH poi.material " +
           "LEFT JOIN FETCH poi.unit " +
           "LEFT JOIN FETCH poi.bom " +
           "LEFT JOIN FETCH poi.salUnit " +
           "LEFT JOIN FETCH poi.subReqOrderItem sroi " +
           "LEFT JOIN FETCH sroi.subReqOrder " +
           "WHERE poi.purchaseOrder.id = :orderId " +
           "ORDER BY poi.sequence")
    List<PurchaseOrderItem> findByPurchaseOrderIdWithDetails(@Param("orderId") Long orderId);
}

