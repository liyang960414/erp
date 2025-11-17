package com.sambound.erp.repository;

import com.sambound.erp.entity.SubReqOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SubReqOrderRepository extends JpaRepository<SubReqOrder, Long> {
    
    /**
     * 根据单据头序号查询订单
     */
    Optional<SubReqOrder> findByBillHeadSeq(Integer billHeadSeq);
    
    /**
     * 检查单据头序号是否存在
     */
    boolean existsByBillHeadSeq(Integer billHeadSeq);
    
    /**
     * 分页查询委外订单
     */
    @Query("SELECT sro FROM SubReqOrder sro " +
           "WHERE (:billHeadSeq IS NULL OR sro.billHeadSeq = :billHeadSeq) " +
           "AND (:status IS NULL OR sro.status = :status) " +
           "AND (:description IS NULL OR sro.description LIKE :description)")
    Page<SubReqOrder> findByConditions(
        @Param("billHeadSeq") Integer billHeadSeq,
        @Param("status") SubReqOrder.OrderStatus status,
        @Param("description") String description,
        Pageable pageable
    );
    
    /**
     * 根据ID查询订单详情
     */
    @Query("SELECT sro FROM SubReqOrder sro " +
           "WHERE sro.id = :id")
    Optional<SubReqOrder> findByIdWithDetails(@Param("id") Long id);
}

