-- ============================================
-- 添加审计日志表
-- 版本: 3.0
-- 创建日期: 2024-12-20
-- 说明: 创建audit_logs表用于记录系统审计日志
-- ============================================

-- 审计日志表
CREATE TABLE audit_logs (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    user_id BIGINT NOT NULL,
    action VARCHAR(50) NOT NULL,
    module VARCHAR(50) NOT NULL,
    resource_type VARCHAR(50),
    resource_id VARCHAR(100),
    description VARCHAR(500),
    request_method VARCHAR(10),
    request_uri VARCHAR(500),
    ip_address VARCHAR(50),
    status VARCHAR(20) NOT NULL,
    error_message VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 创建索引
CREATE INDEX idx_audit_logs_username ON audit_logs(username);
CREATE INDEX idx_audit_logs_user_id ON audit_logs(user_id);
CREATE INDEX idx_audit_logs_action ON audit_logs(action);
CREATE INDEX idx_audit_logs_module ON audit_logs(module);
CREATE INDEX idx_audit_logs_status ON audit_logs(status);
CREATE INDEX idx_audit_logs_created_at ON audit_logs(created_at DESC);
CREATE INDEX idx_audit_logs_resource ON audit_logs(resource_type, resource_id);

-- 添加注释
COMMENT ON TABLE audit_logs IS '审计日志表';
COMMENT ON COLUMN audit_logs.id IS '审计日志ID';
COMMENT ON COLUMN audit_logs.username IS '操作者用户名';
COMMENT ON COLUMN audit_logs.user_id IS '操作者用户ID';
COMMENT ON COLUMN audit_logs.action IS '操作类型（LOGIN, CREATE_USER等）';
COMMENT ON COLUMN audit_logs.module IS '操作模块（AUTH, USER_MANAGEMENT等）';
COMMENT ON COLUMN audit_logs.resource_type IS '目标资源类型（User, Role等）';
COMMENT ON COLUMN audit_logs.resource_id IS '目标资源ID';
COMMENT ON COLUMN audit_logs.description IS '操作详情/描述';
COMMENT ON COLUMN audit_logs.request_method IS '请求方法（GET, POST等）';
COMMENT ON COLUMN audit_logs.request_uri IS '请求URI';
COMMENT ON COLUMN audit_logs.ip_address IS '请求IP地址';
COMMENT ON COLUMN audit_logs.status IS '操作状态（SUCCESS, FAILURE）';
COMMENT ON COLUMN audit_logs.error_message IS '错误信息（如果操作失败）';
COMMENT ON COLUMN audit_logs.created_at IS '操作时间';

