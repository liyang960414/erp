package com.sambound.erp.repository;

import com.sambound.erp.entity.PurchaseOrderDelivery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface PurchaseOrderDeliveryRepository extends JpaRepository<PurchaseOrderDelivery, Long> {
    
    /**
     * 根据采购订单明细ID查询所有交货明细
     */
    List<PurchaseOrderDelivery> findByPurchaseOrderItemId(Long purchaseOrderItemId);
    
    /**
     * 根据采购订单明细ID查询所有交货明细，按序号排序
     */
    @Query("SELECT pod FROM PurchaseOrderDelivery pod " +
           "WHERE pod.purchaseOrderItem.id = :itemId " +
           "ORDER BY pod.sequence")
    List<PurchaseOrderDelivery> findByPurchaseOrderItemIdOrderBySequence(@Param("itemId") Long itemId);
    
    /**
     * 根据采购订单明细ID汇总计划数量
     */
    @Query("SELECT COALESCE(SUM(pod.planQty), 0) FROM PurchaseOrderDelivery pod " +
           "WHERE pod.purchaseOrderItem.id = :itemId")
    Optional<BigDecimal> sumPlanQtyByItemId(@Param("itemId") Long itemId);
}

