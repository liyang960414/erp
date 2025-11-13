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
     * 根据多个父项物料ID批量查找所有版本的BOM
     */
    List<BillOfMaterial> findByMaterialIdIn(List<Long> materialIds);

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

    /**
     * 根据物料编码查询所有版本的BOM
     */
    @Query("SELECT bom FROM BillOfMaterial bom " +
            "LEFT JOIN FETCH bom.material " +
            "WHERE bom.material.code = :materialCode " +
            "ORDER BY bom.version")
    List<BillOfMaterial> findByMaterialCode(@Param("materialCode") String materialCode);

    /**
     * 根据子物料ID查询使用该物料作为子项的所有BOM
     */
    @Query("SELECT DISTINCT bom FROM BillOfMaterial bom " +
            "LEFT JOIN FETCH bom.material " +
            "JOIN BomItem item ON item.bom.id = bom.id " +
            "WHERE item.childMaterial.id = :childMaterialId")
    List<BillOfMaterial> findByChildMaterialId(@Param("childMaterialId") Long childMaterialId);
}

