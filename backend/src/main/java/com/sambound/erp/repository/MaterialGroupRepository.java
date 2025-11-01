package com.sambound.erp.repository;

import com.sambound.erp.entity.MaterialGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MaterialGroupRepository extends JpaRepository<MaterialGroup, Long> {
    Optional<MaterialGroup> findByCode(String code);
    boolean existsByCode(String code);
    
    /**
     * 使用 PostgreSQL 的 INSERT ... ON CONFLICT 实现原子性的插入或获取操作
     * 
     * @param code 物料组编码
     * @param name 物料组名称
     * @return 已存在或新创建的物料组
     */
    @Query(value = """
        INSERT INTO material_groups (code, name, created_at, updated_at)
        VALUES (:code, :name, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        ON CONFLICT (code) DO UPDATE SET code = EXCLUDED.code
        RETURNING id, code, name, description, parent_id, created_at, updated_at
        """, nativeQuery = true)
    MaterialGroup insertOrGetByCode(@Param("code") String code, @Param("name") String name);
    
    @Query(value = """
        INSERT INTO material_groups (code, name, description, parent_id, created_at, updated_at)
        VALUES (:code, :name, :description, :parentId, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        ON CONFLICT (code) DO UPDATE 
        SET name = EXCLUDED.name, 
            description = EXCLUDED.description, 
            parent_id = EXCLUDED.parent_id,
            updated_at = CURRENT_TIMESTAMP
        RETURNING id, code, name, description, parent_id, created_at, updated_at
        """, nativeQuery = true)
    MaterialGroup insertOrGetByCodeWithParent(
        @Param("code") String code, 
        @Param("name") String name,
        @Param("description") String description,
        @Param("parentId") Long parentId
    );
}

