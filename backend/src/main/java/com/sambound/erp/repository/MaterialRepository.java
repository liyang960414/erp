package com.sambound.erp.repository;

import com.sambound.erp.entity.Material;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MaterialRepository extends JpaRepository<Material, Long>, MaterialRepositoryCustom {
    Optional<Material> findByCode(String code);
    boolean existsByCode(String code);
    List<Material> findByMaterialGroupId(Long materialGroupId);
    
    /**
     * 批量查询物料（按编码列表）
     * 
     * @param codes 物料编码列表
     * @return 物料列表
     */
    @Query("SELECT m FROM Material m WHERE m.code IN :codes")
    List<Material> findByCodeIn(@Param("codes") List<String> codes);
    
    /**
     * 使用 PostgreSQL 的 INSERT ... ON CONFLICT 实现原子性的插入或获取操作
     * 
     * @param code 物料编码
     * @param name 物料名称
     * @param materialGroupId 物料组ID
     * @param baseUnitId 基础单位ID
     * @return 已存在或新创建的物料
     */
    @Query(value = """
        INSERT INTO materials (code, name, material_group_id, base_unit_id, erp_cls_id, created_at, updated_at)
        VALUES (:code, :name, :materialGroupId, :baseUnitId, :erpClsId, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        ON CONFLICT (code) DO UPDATE 
        SET name = EXCLUDED.name,
            material_group_id = EXCLUDED.material_group_id,
            base_unit_id = EXCLUDED.base_unit_id,
            erp_cls_id = EXCLUDED.erp_cls_id,
            updated_at = CURRENT_TIMESTAMP
        RETURNING id, code, name, specification, mnemonic_code, old_number, description, erp_cls_id,
                  material_group_id, base_unit_id, created_at, updated_at
        """, nativeQuery = true)
    Material insertOrGetByCode(
        @Param("code") String code,
        @Param("name") String name,
        @Param("materialGroupId") Long materialGroupId,
        @Param("baseUnitId") Long baseUnitId,
        @Param("erpClsId") String erpClsId
    );
    
    /**
     * 物料批量插入数据
     */
    record MaterialBatchData(
        String code,
        String name,
        Long materialGroupId,
        Long baseUnitId,
        String erpClsId
    ) {}
}

