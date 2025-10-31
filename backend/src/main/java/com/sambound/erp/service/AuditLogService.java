package com.sambound.erp.service;

import com.sambound.erp.dto.AuditLogResponse;
import com.sambound.erp.entity.AuditLog;
import com.sambound.erp.repository.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

/**
 * 审计日志Service
 * 异步记录审计日志，不影响主业务流程
 */
@Service
@Transactional(readOnly = true)
public class AuditLogService {

    private static final Logger logger = LoggerFactory.getLogger(AuditLogService.class);
    
    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * 异步保存审计日志
     * @param auditLog 审计日志实体
     * @return CompletableFuture，方便异步处理完成后的回调
     */
    @Async
    @Transactional
    public CompletableFuture<Void> saveAuditLogAsync(AuditLog auditLog) {
        try {
            auditLogRepository.save(auditLog);
            logger.debug("审计日志已保存：{} - {} - {}", 
                    auditLog.getUsername(), 
                    auditLog.getAction(), 
                    auditLog.getStatus());
        } catch (Exception e) {
            logger.error("保存审计日志失败", e);
        }
        return CompletableFuture.completedFuture(null);
    }

    /**
     * 同步保存审计日志（用于关键操作必须确保记录成功）
     * @param auditLog 审计日志实体
     */
    @Transactional
    public void saveAuditLog(AuditLog auditLog) {
        try {
            auditLogRepository.save(auditLog);
            logger.debug("审计日志已保存：{} - {} - {}", 
                    auditLog.getUsername(), 
                    auditLog.getAction(), 
                    auditLog.getStatus());
        } catch (Exception e) {
            logger.error("保存审计日志失败", e);
        }
    }

    /**
     * 查询审计日志分页列表
     */
    public Page<AuditLogResponse> getAuditLogs(Pageable pageable) {
        return auditLogRepository.findAll(pageable)
                .map(this::convertToResponse);
    }

    /**
     * 根据用户名查询审计日志
     */
    public Page<AuditLogResponse> getAuditLogsByUsername(String username, Pageable pageable) {
        return auditLogRepository.findByUsernameOrderByCreatedAtDesc(username, pageable)
                .map(this::convertToResponse);
    }

    /**
     * 根据操作类型查询审计日志
     */
    public Page<AuditLogResponse> getAuditLogsByAction(String action, Pageable pageable) {
        return auditLogRepository.findByActionOrderByCreatedAtDesc(action, pageable)
                .map(this::convertToResponse);
    }

    /**
     * 根据模块查询审计日志
     */
    public Page<AuditLogResponse> getAuditLogsByModule(String module, Pageable pageable) {
        return auditLogRepository.findByModuleOrderByCreatedAtDesc(module, pageable)
                .map(this::convertToResponse);
    }

    /**
     * 根据状态查询审计日志
     */
    public Page<AuditLogResponse> getAuditLogsByStatus(String status, Pageable pageable) {
        return auditLogRepository.findByStatusOrderByCreatedAtDesc(status, pageable)
                .map(this::convertToResponse);
    }

    /**
     * 根据时间范围查询审计日志
     */
    public Page<AuditLogResponse> getAuditLogsByTimeRange(
            LocalDateTime startTime, 
            LocalDateTime endTime, 
            Pageable pageable) {
        return auditLogRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(startTime, endTime, pageable)
                .map(this::convertToResponse);
    }

    /**
     * 多条件查询审计日志
     */
    public Page<AuditLogResponse> getAuditLogsByConditions(
            String username,
            String action,
            String module,
            String status,
            LocalDateTime startTime,
            LocalDateTime endTime,
            Pageable pageable) {
        return auditLogRepository.findByMultipleConditions(
                username, action, module, status, startTime, endTime, pageable)
                .map(this::convertToResponse);
    }

    /**
     * 转换为响应DTO
     */
    private AuditLogResponse convertToResponse(AuditLog auditLog) {
        return new AuditLogResponse(
                auditLog.getId(),
                auditLog.getUsername(),
                auditLog.getUserId(),
                auditLog.getAction(),
                auditLog.getModule(),
                auditLog.getResourceType(),
                auditLog.getResourceId(),
                auditLog.getDescription(),
                auditLog.getRequestMethod(),
                auditLog.getRequestUri(),
                auditLog.getIpAddress(),
                auditLog.getStatus(),
                auditLog.getErrorMessage(),
                auditLog.getCreatedAt()
        );
    }
}

