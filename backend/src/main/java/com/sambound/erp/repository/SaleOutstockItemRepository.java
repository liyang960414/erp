package com.sambound.erp.repository;

import com.sambound.erp.entity.SaleOutstockItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SaleOutstockItemRepository extends JpaRepository<SaleOutstockItem, Long> {

    @Query("SELECT soi FROM SaleOutstockItem soi " +
           "LEFT JOIN FETCH soi.material " +
           "LEFT JOIN FETCH soi.unit " +
           "LEFT JOIN FETCH soi.saleOrderItem soItem " +
           "LEFT JOIN FETCH soItem.saleOrder " +
           "WHERE soi.saleOutstock.id = :saleOutstockId " +
           "ORDER BY soi.sequence")
    List<SaleOutstockItem> findDetailedBySaleOutstockId(@Param("saleOutstockId") Long saleOutstockId);

    @Query("SELECT soi.saleOutstock.id AS outstockId, COUNT(soi.id) AS itemCount, COALESCE(SUM(soi.qty), 0) AS totalQty " +
           "FROM SaleOutstockItem soi " +
           "WHERE soi.saleOutstock.id IN :outstockIds " +
           "GROUP BY soi.saleOutstock.id")
    List<Object[]> findSummaryBySaleOutstockIds(@Param("outstockIds") List<Long> outstockIds);
}

