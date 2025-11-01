package com.sambound.erp.repository;

import com.sambound.erp.entity.UnitGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UnitGroupRepository extends JpaRepository<UnitGroup, Long> {
    Optional<UnitGroup> findByCode(String code);
    boolean existsByCode(String code);
    
    /**
     * 使用 PostgreSQL 的 INSERT ... ON CONFLICT 实现原子性的插入或获取操作
     * 避免并发创建时的死锁问题
     * 使用 DO UPDATE 而不是 DO NOTHING，确保总是返回一行，避免后续查询导致的死锁
     * 
     * @param code 单位组编码
     * @param name 单位组名称
     * @return 已存在或新创建的单位组
     */
    @Query(value = """
        INSERT INTO unit_groups (code, name, created_at, updated_at)
        VALUES (:code, :name, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        ON CONFLICT (code) DO UPDATE SET code = EXCLUDED.code
        RETURNING id, code, name, description, created_at, updated_at
        """, nativeQuery = true)
    UnitGroup insertOrGetByCode(@Param("code") String code, @Param("name") String name);
}

