package com.sambound.erp.repository;

import com.sambound.erp.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

/**
 * 审计日志Repository
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {
    
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
}

