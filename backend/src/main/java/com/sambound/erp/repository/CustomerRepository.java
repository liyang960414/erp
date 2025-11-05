package com.sambound.erp.repository;

import com.sambound.erp.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {
    Optional<Customer> findByCode(String code);
    boolean existsByCode(String code);
    
    /**
     * 根据编码列表批量查询客户
     * 
     * @param codes 客户编码列表
     * @return 客户列表
     */
    @Query("SELECT c FROM Customer c WHERE c.code IN :codes")
    List<Customer> findByCodeIn(@Param("codes") List<String> codes);
    
    /**
     * 使用 PostgreSQL 的 INSERT ... ON CONFLICT 实现原子性的插入或获取操作
     * 
     * @param code 客户编码
     * @param name 客户名称
     * @return 已存在或新创建的客户
     */
    @Query(value = """
        INSERT INTO customers (code, name, created_at, updated_at)
        VALUES (:code, :name, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        ON CONFLICT (code) DO UPDATE 
        SET name = EXCLUDED.name,
            updated_at = CURRENT_TIMESTAMP
        RETURNING id, code, name, created_at, updated_at
        """, nativeQuery = true)
    Customer insertOrGetByCode(@Param("code") String code, @Param("name") String name);
}
