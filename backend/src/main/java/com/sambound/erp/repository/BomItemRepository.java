package com.sambound.erp.repository;

import com.sambound.erp.entity.BomItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BomItemRepository extends JpaRepository<BomItem, Long> {
    
    /**
     * 根据BOM ID查找所有明细项
     */
    List<BomItem> findByBomId(Long bomId);
    
    /**
     * 根据BOM ID查找所有明细项，按序号排序
     */
    List<BomItem> findByBomIdOrderBySequenceAsc(Long bomId);
    
    /**
     * 查询BOM明细项及其关联的子项物料和单位
     */
    @Query("SELECT item FROM BomItem item " +
           "LEFT JOIN FETCH item.childMaterial " +
           "LEFT JOIN FETCH item.childUnit " +
           "WHERE item.bom.id = :bomId " +
           "ORDER BY item.sequence ASC")
    List<BomItem> findByBomIdWithDetails(@Param("bomId") Long bomId);
    
    /**
     * 根据BOM ID和子物料ID查找BOM明细项（用于反查时获取用量信息）
     */
    @Query("SELECT item FROM BomItem item " +
           "LEFT JOIN FETCH item.childMaterial " +
           "LEFT JOIN FETCH item.childUnit " +
           "WHERE item.bom.id = :bomId AND item.childMaterial.id = :childMaterialId")
    List<BomItem> findByBomIdAndChildMaterialId(@Param("bomId") Long bomId, 
                                                  @Param("childMaterialId") Long childMaterialId);
    
    /**
     * 删除指定BOM的所有明细项
     */
    void deleteByBomId(Long bomId);
}

