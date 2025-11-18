package com.sambound.erp.repository;

import com.sambound.erp.entity.SubReqOrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

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
    
    /**
     * 根据委外订单ID和sequence查找明细
     */
    Optional<SubReqOrderItem> findBySubReqOrderIdAndSequence(Long subReqOrderId, Integer sequence);
    
    /**
     * 根据物料ID和sequence查找委外订单明细
     * 注意：由于sequence是委外订单内的序号，可能返回多个结果，此方法返回第一个匹配的
     */
    @Query("SELECT sroi FROM SubReqOrderItem sroi " +
           "WHERE sroi.material.id = :materialId AND sroi.sequence = :sequence " +
           "ORDER BY sroi.id")
    List<SubReqOrderItem> findByMaterialIdAndSequence(@Param("materialId") Long materialId, @Param("sequence") Integer sequence);
}

