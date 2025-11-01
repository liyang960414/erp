-- ============================================
-- ERP系统数据库初始化脚本
-- 版本: 1.0
-- 创建日期: 2024-01-01
-- 说明: 创建所有表结构，不含初始数据
-- ============================================

-- 删除外键约束（如果存在）
DROP TABLE IF EXISTS unit_conversions CASCADE;
DROP TABLE IF EXISTS units CASCADE;
DROP TABLE IF EXISTS unit_groups CASCADE;
DROP TABLE IF EXISTS audit_logs CASCADE;
DROP TABLE IF EXISTS user_roles CASCADE;
DROP TABLE IF EXISTS role_permissions CASCADE;
DROP TABLE IF EXISTS users CASCADE;
DROP TABLE IF EXISTS roles CASCADE;
DROP TABLE IF EXISTS permissions CASCADE;

-- ============================================
-- 权限表
-- ============================================
CREATE TABLE permissions (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(200),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ============================================
-- 角色表
-- ============================================
CREATE TABLE roles (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(200),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ============================================
-- 用户表
-- ============================================
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(100) NOT NULL,
    email VARCHAR(100) UNIQUE,
    full_name VARCHAR(100),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    account_non_expired BOOLEAN NOT NULL DEFAULT TRUE,
    account_non_locked BOOLEAN NOT NULL DEFAULT TRUE,
    credentials_non_expired BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ============================================
-- 角色-权限关联表（多对多）
-- ============================================
CREATE TABLE role_permissions (
    role_id BIGINT NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    permission_id BIGINT NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

-- ============================================
-- 用户-角色关联表（多对多）
-- ============================================
CREATE TABLE user_roles (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id BIGINT NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

-- ============================================
-- 审计日志表
-- ============================================
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

-- ============================================
-- 单位组表
-- ============================================
CREATE TABLE unit_groups (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(200),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ============================================
-- 单位表
-- ============================================
CREATE TABLE units (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    unit_group_id BIGINT NOT NULL REFERENCES unit_groups(id) ON DELETE CASCADE,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ============================================
-- 单位转换表
-- ============================================
CREATE TABLE unit_conversions (
    id BIGSERIAL PRIMARY KEY,
    from_unit_id BIGINT NOT NULL REFERENCES units(id) ON DELETE CASCADE,
    to_unit_id BIGINT NOT NULL REFERENCES units(id) ON DELETE CASCADE,
    convert_type VARCHAR(20) NOT NULL DEFAULT 'FIXED',
    numerator DECIMAL(18, 6) NOT NULL,
    denominator DECIMAL(18, 6) NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_unit_conversion_self CHECK (from_unit_id != to_unit_id),
    CONSTRAINT chk_unit_conversion_denominator CHECK (denominator > 0)
);

-- ============================================
-- 创建索引
-- ============================================

-- 用户表索引
CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_enabled ON users(enabled);

-- 角色表索引
CREATE INDEX idx_roles_name ON roles(name);

-- 权限表索引
CREATE INDEX idx_permissions_name ON permissions(name);

-- 审计日志表索引
CREATE INDEX idx_audit_logs_username ON audit_logs(username);
CREATE INDEX idx_audit_logs_user_id ON audit_logs(user_id);
CREATE INDEX idx_audit_logs_action ON audit_logs(action);
CREATE INDEX idx_audit_logs_module ON audit_logs(module);
CREATE INDEX idx_audit_logs_status ON audit_logs(status);
CREATE INDEX idx_audit_logs_created_at ON audit_logs(created_at DESC);
CREATE INDEX idx_audit_logs_resource ON audit_logs(resource_type, resource_id);

-- 单位组表索引
CREATE INDEX idx_unit_groups_code ON unit_groups(code);

-- 单位表索引
CREATE INDEX idx_units_code ON units(code);
CREATE INDEX idx_units_unit_group_id ON units(unit_group_id);
CREATE INDEX idx_units_enabled ON units(enabled);

-- 单位转换表索引
CREATE INDEX idx_unit_conversions_from_unit_id ON unit_conversions(from_unit_id);
CREATE INDEX idx_unit_conversions_to_unit_id ON unit_conversions(to_unit_id);

-- ============================================
-- 添加注释
-- ============================================
COMMENT ON TABLE users IS '用户表';
COMMENT ON TABLE roles IS '角色表';
COMMENT ON TABLE permissions IS '权限表';
COMMENT ON TABLE user_roles IS '用户角色关联表';
COMMENT ON TABLE role_permissions IS '角色权限关联表';
COMMENT ON TABLE audit_logs IS '审计日志表';
COMMENT ON TABLE unit_groups IS '单位组表';
COMMENT ON TABLE units IS '单位表';
COMMENT ON TABLE unit_conversions IS '单位转换表';

COMMENT ON COLUMN users.id IS '用户ID';
COMMENT ON COLUMN users.username IS '用户名（唯一）';
COMMENT ON COLUMN users.password IS '加密后的密码';
COMMENT ON COLUMN users.email IS '邮箱（唯一）';
COMMENT ON COLUMN users.full_name IS '用户全名';
COMMENT ON COLUMN users.enabled IS '账户是否启用';
COMMENT ON COLUMN users.account_non_expired IS '账户是否未过期';
COMMENT ON COLUMN users.account_non_locked IS '账户是否未锁定';
COMMENT ON COLUMN users.credentials_non_expired IS '凭证是否未过期';
COMMENT ON COLUMN users.created_at IS '创建时间';
COMMENT ON COLUMN users.updated_at IS '更新时间';

COMMENT ON COLUMN roles.id IS '角色ID';
COMMENT ON COLUMN roles.name IS '角色名称（唯一）';
COMMENT ON COLUMN roles.description IS '角色描述';

COMMENT ON COLUMN permissions.id IS '权限ID';
COMMENT ON COLUMN permissions.name IS '权限名称（唯一）';
COMMENT ON COLUMN permissions.description IS '权限描述';

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

COMMENT ON COLUMN unit_groups.id IS '单位组ID';
COMMENT ON COLUMN unit_groups.code IS '单位组编码（唯一）';
COMMENT ON COLUMN unit_groups.name IS '单位组名称';
COMMENT ON COLUMN unit_groups.description IS '单位组描述';
COMMENT ON COLUMN unit_groups.created_at IS '创建时间';
COMMENT ON COLUMN unit_groups.updated_at IS '更新时间';

COMMENT ON COLUMN units.id IS '单位ID';
COMMENT ON COLUMN units.code IS '单位编码（唯一）';
COMMENT ON COLUMN units.name IS '单位名称';
COMMENT ON COLUMN units.unit_group_id IS '所属单位组ID';
COMMENT ON COLUMN units.enabled IS '是否启用';
COMMENT ON COLUMN units.created_at IS '创建时间';
COMMENT ON COLUMN units.updated_at IS '更新时间';

COMMENT ON COLUMN unit_conversions.id IS '单位转换ID';
COMMENT ON COLUMN unit_conversions.from_unit_id IS '源单位ID';
COMMENT ON COLUMN unit_conversions.to_unit_id IS '目标单位ID';
COMMENT ON COLUMN unit_conversions.convert_type IS '换算类型（FIXED-固定, FLOAT-浮动）';
COMMENT ON COLUMN unit_conversions.numerator IS '换算分子';
COMMENT ON COLUMN unit_conversions.denominator IS '换算分母';
COMMENT ON COLUMN unit_conversions.created_at IS '创建时间';

