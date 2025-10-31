package com.sambound.erp.util;

import com.sambound.erp.entity.AuditLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

/**
 * 审计日志辅助工具类
 * 提供便捷的方法来构建和记录审计日志
 */
public class AuditLogHelper {

    private static final Logger logger = LoggerFactory.getLogger(AuditLogHelper.class);

    /**
     * 创建成功审计日志的Builder
     */
    public static Builder success() {
        return new Builder("SUCCESS");
    }

    /**
     * 创建失败审计日志的Builder
     */
    public static Builder failure(String errorMessage) {
        return new Builder("FAILURE").errorMessage(errorMessage);
    }

    /**
     * 审计日志Builder
     */
    public static class Builder {
        private final String status;
        private String username;
        private Long userId;
        private String action;
        private String module;
        private String resourceType;
        private String resourceId;
        private String description;
        private String requestMethod;
        private String requestUri;
        private String ipAddress;
        private String errorMessage;

        public Builder(String status) {
            this.status = status;
        }

        public Builder username(String username) {
            this.username = username;
            return this;
        }

        public Builder userId(Long userId) {
            this.userId = userId;
            return this;
        }

        public Builder action(String action) {
            this.action = action;
            return this;
        }

        public Builder module(String module) {
            this.module = module;
            return this;
        }

        public Builder resourceType(String resourceType) {
            this.resourceType = resourceType;
            return this;
        }

        public Builder resourceId(String resourceId) {
            this.resourceId = resourceId;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder requestMethod(String requestMethod) {
            this.requestMethod = requestMethod;
            return this;
        }

        public Builder requestUri(String requestUri) {
            this.requestUri = requestUri;
            return this;
        }

        public Builder ipAddress(String ipAddress) {
            this.ipAddress = ipAddress;
            return this;
        }

        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public AuditLog build() {
            return AuditLog.builder()
                    .username(username)
                    .userId(userId)
                    .action(action)
                    .module(module)
                    .resourceType(resourceType)
                    .resourceId(resourceId != null ? resourceId.toString() : null)
                    .description(description)
                    .requestMethod(requestMethod)
                    .requestUri(requestUri)
                    .ipAddress(ipAddress)
                    .status(status)
                    .errorMessage(errorMessage)
                    .build();
        }
    }

    // ========== 便捷方法：常见操作类型 ==========

    // 认证相关
    public static final String ACTION_LOGIN = "LOGIN";
    public static final String ACTION_LOGOUT = "LOGOUT";
    public static final String ACTION_REGISTER = "REGISTER";
    
    // 用户管理
    public static final String ACTION_CREATE_USER = "CREATE_USER";
    public static final String ACTION_UPDATE_USER = "UPDATE_USER";
    public static final String ACTION_DELETE_USER = "DELETE_USER";
    public static final String ACTION_CHANGE_PASSWORD = "CHANGE_PASSWORD";
    public static final String ACTION_ENABLE_USER = "ENABLE_USER";
    public static final String ACTION_DISABLE_USER = "DISABLE_USER";
    
    // 角色管理
    public static final String ACTION_CREATE_ROLE = "CREATE_ROLE";
    public static final String ACTION_UPDATE_ROLE = "UPDATE_ROLE";
    public static final String ACTION_DELETE_ROLE = "DELETE_ROLE";
    
    // 权限管理
    public static final String ACTION_CREATE_PERMISSION = "CREATE_PERMISSION";
    public static final String ACTION_UPDATE_PERMISSION = "UPDATE_PERMISSION";
    public static final String ACTION_DELETE_PERMISSION = "DELETE_PERMISSION";
    
    // 模块类型
    public static final String MODULE_AUTH = "AUTH";
    public static final String MODULE_USER_MANAGEMENT = "USER_MANAGEMENT";
    public static final String MODULE_ROLE_MANAGEMENT = "ROLE_MANAGEMENT";
    public static final String MODULE_PERMISSION_MANAGEMENT = "PERMISSION_MANAGEMENT";
    
    // 资源类型
    public static final String RESOURCE_TYPE_USER = "User";
    public static final String RESOURCE_TYPE_ROLE = "Role";
    public static final String RESOURCE_TYPE_PERMISSION = "Permission";
}

