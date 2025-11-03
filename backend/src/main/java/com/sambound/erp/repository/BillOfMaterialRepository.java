package com.sambound.erp.repository;

import com.sambound.erp.entity.BillOfMaterial;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BillOfMaterialRepository extends JpaRepository<BillOfMaterial, Long> {
    
    /**
     * 根据父项物料ID查找所有版本的BOM
     */
    List<BillOfMaterial> findByMaterialId(Long materialId);
    
    /**
     * 根据父项物料ID和版本号查找BOM
     */
    Optional<BillOfMaterial> findByMaterialIdAndVersion(Long materialId, String version);
    
    /**
     * 检查是否存在指定物料和版本的BOM
     */
    boolean existsByMaterialIdAndVersion(Long materialId, String version);
    
    /**
     * 查询BOM及其关联的父项物料和明细项
     */
    @Query("SELECT bom FROM BillOfMaterial bom " +
           "LEFT JOIN FETCH bom.material " +
           "WHERE bom.id = :id")
    Optional<BillOfMaterial> findByIdWithMaterial(@Param("id") Long id);
    
    /**
     * 查询所有BOM及其关联的父项物料
     */
    @Query("SELECT bom FROM BillOfMaterial bom " +
           "LEFT JOIN FETCH bom.material " +
           "ORDER BY bom.material.code, bom.version")
    List<BillOfMaterial> findAllWithMaterial();
}

