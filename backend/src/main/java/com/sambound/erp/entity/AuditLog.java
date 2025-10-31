package com.sambound.erp.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 审计日志实体类
 * 记录系统中所有的关键操作，用于审计和追踪
 */
@Entity
@Table(name = "audit_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * 操作者用户名
     */
    @Column(nullable = false, length = 50)
    private String username;
    
    /**
     * 操作者用户ID
     */
    @Column(nullable = false)
    private Long userId;
    
    /**
     * 操作类型（如：LOGIN, CREATE_USER, UPDATE_USER, DELETE_USER等）
     */
    @Column(nullable = false, length = 50)
    private String action;
    
    /**
     * 操作模块（如：AUTH, USER_MANAGEMENT, ROLE_MANAGEMENT等）
     */
    @Column(nullable = false, length = 50)
    private String module;
    
    /**
     * 目标资源类型（如：User, Role, Permission等）
     */
    @Column(length = 50)
    private String resourceType;
    
    /**
     * 目标资源ID
     */
    @Column(length = 100)
    private String resourceId;
    
    /**
     * 操作详情/描述
     */
    @Column(length = 500)
    private String description;
    
    /**
     * 请求方法（如：GET, POST, PUT, DELETE）
     */
    @Column(length = 10)
    private String requestMethod;
    
    /**
     * 请求URI
     */
    @Column(length = 500)
    private String requestUri;
    
    /**
     * 请求IP地址
     */
    @Column(length = 50)
    private String ipAddress;
    
    /**
     * 操作状态（SUCCESS, FAILURE）
     */
    @Column(nullable = false, length = 20)
    private String status;
    
    /**
     * 错误信息（如果操作失败）
     */
    @Column(length = 1000)
    private String errorMessage;
    
    /**
     * 操作时间
     */
    @Column(nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}

