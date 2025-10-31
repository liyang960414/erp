package com.sambound.erp.repository;

import com.sambound.erp.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

/**
 * 审计日志Repository
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    
    /**
     * 根据用户名查询审计日志
     */
    Page<AuditLog> findByUsernameOrderByCreatedAtDesc(String username, Pageable pageable);
    
    /**
     * 根据操作类型查询审计日志
     */
    Page<AuditLog> findByActionOrderByCreatedAtDesc(String action, Pageable pageable);
    
    /**
     * 根据操作模块查询审计日志
     */
    Page<AuditLog> findByModuleOrderByCreatedAtDesc(String module, Pageable pageable);
    
    /**
     * 根据状态查询审计日志
     */
    Page<AuditLog> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);
    
    /**
     * 根据时间范围查询审计日志
     */
    Page<AuditLog> findByCreatedAtBetweenOrderByCreatedAtDesc(
            LocalDateTime startTime, 
            LocalDateTime endTime, 
            Pageable pageable
    );
    
    /**
     * 根据多个条件查询审计日志
     */
    @Query("SELECT al FROM AuditLog al WHERE " +
           "(:username IS NULL OR al.username = :username) AND " +
           "(:action IS NULL OR al.action = :action) AND " +
           "(:module IS NULL OR al.module = :module) AND " +
           "(:status IS NULL OR al.status = :status) AND " +
           "(:startTime IS NULL OR al.createdAt >= :startTime) AND " +
           "(:endTime IS NULL OR al.createdAt <= :endTime) " +
           "ORDER BY al.createdAt DESC")
    Page<AuditLog> findByMultipleConditions(
            @Param("username") String username,
            @Param("action") String action,
            @Param("module") String module,
            @Param("status") String status,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            Pageable pageable
    );
}

