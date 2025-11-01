package com.sambound.erp.repository;

import com.sambound.erp.entity.Unit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UnitRepository extends JpaRepository<Unit, Long> {
    Optional<Unit> findByCode(String code);
    boolean existsByCode(String code);
    List<Unit> findByUnitGroupId(Long unitGroupId);
    List<Unit> findByUnitGroupIdAndEnabledTrue(Long unitGroupId);
    List<Unit> findByEnabledTrue();
    
    @Query("SELECT u FROM Unit u JOIN FETCH u.unitGroup WHERE u.id = :id")
    Optional<Unit> findByIdWithUnitGroup(@Param("id") Long id);
    
    @Query("SELECT u FROM Unit u JOIN FETCH u.unitGroup ORDER BY u.unitGroup.code, u.code")
    List<Unit> findAllWithUnitGroup();
    
    /**
     * 使用 PostgreSQL 的 INSERT ... ON CONFLICT 实现原子性的插入或获取操作
     * 避免并发创建时的死锁问题
     * 使用 DO UPDATE 而不是 DO NOTHING，确保总是返回一行，避免后续查询导致的死锁
     * 
     * @param code 单位编码
     * @param name 单位名称
     * @param unitGroupId 单位组ID
     * @return 已存在或新创建的单位
     */
    @Query(value = """
        INSERT INTO units (code, name, unit_group_id, enabled, conversion_denominator, created_at, updated_at)
        VALUES (:code, :name, :unitGroupId, TRUE, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        ON CONFLICT (code) DO UPDATE SET code = EXCLUDED.code
        RETURNING id, code, name, unit_group_id, enabled, conversion_numerator, conversion_denominator, created_at, updated_at
        """, nativeQuery = true)
    Unit insertOrGetByCode(@Param("code") String code, @Param("name") String name, @Param("unitGroupId") Long unitGroupId);
}

