package com.sambound.erp.repository;

import com.sambound.erp.entity.SaleOutstock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface SaleOutstockRepository extends JpaRepository<SaleOutstock, Long> {
    Optional<SaleOutstock> findByBillNo(String billNo);

    @Query("SELECT so.billNo FROM SaleOutstock so WHERE so.billNo IN :billNos")
    List<String> findExistingBillNos(@Param("billNos") Collection<String> billNos);

    @Query("SELECT so FROM SaleOutstock so " +
           "WHERE (:billNo IS NULL OR LOWER(so.billNo) LIKE :billNo) " +
           "AND (:startDate IS NULL OR so.outstockDate >= :startDate) " +
           "AND (:endDate IS NULL OR so.outstockDate <= :endDate)")
    Page<SaleOutstock> findByConditions(
        @Param("billNo") String billNo,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate,
        Pageable pageable
    );

    @Query("SELECT DISTINCT so FROM SaleOutstock so " +
           "LEFT JOIN FETCH so.items items " +
           "LEFT JOIN FETCH items.material " +
           "LEFT JOIN FETCH items.unit " +
           "LEFT JOIN FETCH items.saleOrderItem soi " +
           "LEFT JOIN FETCH soi.saleOrder " +
           "WHERE so.id = :id")
    Optional<SaleOutstock> findByIdWithItems(@Param("id") Long id);
}

