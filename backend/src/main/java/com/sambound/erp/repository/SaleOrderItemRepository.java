package com.sambound.erp.repository;

import com.sambound.erp.entity.SaleOrderItem;
import com.sambound.erp.enums.SaleOrderItemStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
     * 查询需要提醒的订单明细（优化版：直接在数据库层面过滤）
     * 包含订单、物料、单位信息
     * 
     * @param today 当前日期
     * @param inspectionDateStart 验货日期范围开始（今天）
     * @param inspectionDateEnd 验货日期范围结束（今天+40天）
     * @param deliveryDateEnd 要货日期范围结束（今天，用于查询超期的）
     */
    @Query("SELECT soi FROM SaleOrderItem soi " +
           "LEFT JOIN FETCH soi.saleOrder so " +
           "LEFT JOIN FETCH so.customer " +
           "LEFT JOIN FETCH soi.material " +
           "LEFT JOIN FETCH soi.unit " +
           "WHERE (" +
           "  (soi.inspectionDate IS NOT NULL " +
           "   AND soi.inspectionDate >= :inspectionDateStart " +
           "   AND soi.inspectionDate <= :inspectionDateEnd) " +
           "  OR " +
           "  (soi.deliveryDate IS NOT NULL " +
           "   AND soi.deliveryDate < :deliveryDateEndTime)" +
           ") " +
           "ORDER BY soi.id")
    List<SaleOrderItem> findAlertsForDateRange(
        @Param("inspectionDateStart") LocalDate inspectionDateStart,
        @Param("inspectionDateEnd") LocalDate inspectionDateEnd,
        @Param("deliveryDateEndTime") LocalDateTime deliveryDateEndTime
    );

    List<SaleOrderItem> findBySequenceIn(List<Integer> sequences);

    boolean existsBySaleOrderIdAndStatus(Long saleOrderId, SaleOrderItemStatus status);
}
