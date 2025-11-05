package com.sambound.erp.repository;

import com.sambound.erp.entity.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SupplierRepository extends JpaRepository<Supplier, Long> {
    Optional<Supplier> findByCode(String code);
    boolean existsByCode(String code);
    
    /**
     * 根据编码列表批量查询供应商
     * 
     * @param codes 供应商编码列表
     * @return 供应商列表
     */
    @Query("SELECT s FROM Supplier s WHERE s.code IN :codes")
    List<Supplier> findByCodeIn(@Param("codes") List<String> codes);
    
    /**
     * 使用 PostgreSQL 的 INSERT ... ON CONFLICT 实现原子性的插入或获取操作
     * 
     * @param code 供应商编码
     * @param name 供应商名称
     * @param shortName 简称
     * @param englishName 英文名称
     * @param description 描述
     * @return 已存在或新创建的供应商
     */
    @Query(value = """
        INSERT INTO suppliers (code, name, short_name, english_name, description, created_at, updated_at)
        VALUES (:code, :name, :shortName, :englishName, :description, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        ON CONFLICT (code) DO UPDATE 
        SET name = EXCLUDED.name,
            short_name = EXCLUDED.short_name,
            english_name = EXCLUDED.english_name,
            description = EXCLUDED.description,
            updated_at = CURRENT_TIMESTAMP
        RETURNING id, code, name, short_name, english_name, description, created_at, updated_at
        """, nativeQuery = true)
    Supplier insertOrGetByCode(
            @Param("code") String code, 
            @Param("name") String name,
            @Param("shortName") String shortName,
            @Param("englishName") String englishName,
            @Param("description") String description
    );
}

