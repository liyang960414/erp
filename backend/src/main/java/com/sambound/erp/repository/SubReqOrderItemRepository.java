package com.sambound.erp.repository;

import com.sambound.erp.entity.SubReqOrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SubReqOrderItemRepository extends JpaRepository<SubReqOrderItem, Long> {
    
    /**
     * 根据委外订单ID查询所有明细
     */
    List<SubReqOrderItem> findBySubReqOrderId(Long subReqOrderId);
    
    /**
     * 根据委外订单ID查询所有明细，包含关联的物料、单位、BOM、供应商信息
     */
    @Query("SELECT sroi FROM SubReqOrderItem sroi " +
           "LEFT JOIN FETCH sroi.material " +
           "LEFT JOIN FETCH sroi.unit " +
           "LEFT JOIN FETCH sroi.bom " +
           "LEFT JOIN FETCH sroi.supplier " +
           "WHERE sroi.subReqOrder.id = :orderId " +
           "ORDER BY sroi.sequence")
    List<SubReqOrderItem> findBySubReqOrderIdWithDetails(@Param("orderId") Long orderId);
}

