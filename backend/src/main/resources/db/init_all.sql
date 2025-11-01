-- ============================================
-- ERP系统数据库完整初始化脚本
-- 版本: 3.0
-- 创建日期: 2024-12-20
-- 说明: 一次性完成所有表结构和初始数据的初始化
-- 包含：用户、角色、权限、审计日志等所有表
-- ============================================

-- 开始事务
BEGIN;

-- ============================================
-- 第一步：删除所有现有表
-- ============================================
DROP TABLE IF EXISTS audit_logs CASCADE;
DROP TABLE IF EXISTS user_roles CASCADE;
DROP TABLE IF EXISTS role_permissions CASCADE;
DROP TABLE IF EXISTS users CASCADE;
DROP TABLE IF EXISTS roles CASCADE;
DROP TABLE IF EXISTS permissions CASCADE;

-- ============================================
-- 第二步：创建表结构
-- ============================================

-- 权限表
CREATE TABLE permissions (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(200),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 角色表
CREATE TABLE roles (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(200),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 用户表
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

-- 角色-权限关联表（多对多）
CREATE TABLE role_permissions (
    role_id BIGINT NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    permission_id BIGINT NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

-- 用户-角色关联表（多对多）
CREATE TABLE user_roles (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id BIGINT NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

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

-- ============================================
-- 第三步：创建索引
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

-- ============================================
-- 第四步：添加表注释和列注释
-- ============================================

COMMENT ON TABLE users IS '用户表';
COMMENT ON TABLE roles IS '角色表';
COMMENT ON TABLE permissions IS '权限表';
COMMENT ON TABLE user_roles IS '用户角色关联表';
COMMENT ON TABLE role_permissions IS '角色权限关联表';
COMMENT ON TABLE audit_logs IS '审计日志表';

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

-- ============================================
-- 第五步：插入初始数据
-- ============================================

-- 插入权限数据
INSERT INTO permissions (name, description) VALUES
-- 用户权限
('user:read', '查看用户'),
('user:write', '创建/编辑用户'),
('user:delete', '删除用户'),
-- 产品权限
('product:read', '查看产品'),
('product:write', '创建/编辑产品'),
('product:delete', '删除产品'),
-- 订单权限
('order:read', '查看订单'),
('order:write', '创建/编辑订单'),
('order:delete', '删除订单'),
-- 系统权限
('system:read', '查看系统设置'),
('system:write', '修改系统设置'),
('system:delete', '删除系统设置');

-- 插入角色数据
INSERT INTO roles (name, description) VALUES
('ADMIN', '系统管理员，拥有所有权限'),
('USER', '普通用户，拥有基本的查看权限'),
('MANAGER', '经理，拥有产品和订单的管理权限');

-- 为ADMIN角色分配所有权限
INSERT INTO role_permissions (role_id, permission_id)
SELECT 
    r.id,
    p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'ADMIN';

-- 为USER角色分配基本查看权限
INSERT INTO role_permissions (role_id, permission_id)
SELECT 
    r.id,
    p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'USER' 
    AND p.name IN ('user:read', 'product:read', 'order:read');

-- 为MANAGER角色分配管理权限
INSERT INTO role_permissions (role_id, permission_id)
SELECT 
    r.id,
    p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'MANAGER' 
    AND p.name IN (
        'user:read', 'product:read', 'product:write', 'product:delete',
        'order:read', 'order:write', 'order:delete', 'system:read'
    );

-- 插入默认管理员用户
-- 密码: admin123 (BCrypt加密后的值)
INSERT INTO users (
    username, 
    password, 
    email, 
    full_name, 
    enabled, 
    account_non_expired, 
    account_non_locked, 
    credentials_non_expired
) VALUES (
    'admin',
    '$2a$10$frXVPPQ6Adx7L0RUmd3FNezNyWPzJEaZlR46qEBQnQyjyBdELowiW',
    'admin@erp.example.com',
    '系统管理员',
    TRUE,
    TRUE,
    TRUE,
    TRUE
);

-- 为管理员分配ADMIN角色
INSERT INTO user_roles (user_id, role_id)
SELECT 
    u.id,
    r.id
FROM users u
INNER JOIN roles r ON r.name = 'ADMIN'
WHERE u.username = 'admin';

-- 插入测试用户（可选）
INSERT INTO users (
    username, 
    password, 
    email, 
    full_name, 
    enabled, 
    account_non_expired, 
    account_non_locked, 
    credentials_non_expired
) VALUES (
    'testuser',
    '$2a$10$frXVPPQ6Adx7L0RUmd3FNezNyWPzJEaZlR46qEBQnQyjyBdELowiW',
    'testuser@erp.example.com',
    '测试用户',
    TRUE,
    TRUE,
    TRUE,
    TRUE
);

-- 为测试用户分配USER角色
INSERT INTO user_roles (user_id, role_id)
SELECT 
    u.id,
    r.id
FROM users u
INNER JOIN roles r ON r.name = 'USER'
WHERE u.username = 'testuser';

-- ============================================
-- 第六步：验证数据
-- ============================================
SELECT '初始化完成！' AS status;

SELECT 
    '用户表' AS 表名, COUNT(*) AS 记录数 FROM users
UNION ALL
SELECT '角色表', COUNT(*) FROM roles
UNION ALL
SELECT '权限表', COUNT(*) FROM permissions
UNION ALL
SELECT '审计日志表', COUNT(*) FROM audit_logs;

SELECT 
    u.username,
    u.email,
    r.name AS role_name,
    COUNT(p.id) AS permission_count
FROM users u
LEFT JOIN user_roles ur ON u.id = ur.user_id
LEFT JOIN roles r ON ur.role_id = r.id
LEFT JOIN role_permissions rp ON r.id = rp.role_id
LEFT JOIN permissions p ON rp.permission_id = p.id
GROUP BY u.username, u.email, r.name
ORDER BY u.username, r.name;

-- 提交事务
COMMIT;

-- ============================================
-- 初始化完成提示
-- ============================================
\echo ''
\echo '========================================'
\echo 'ERP系统数据库初始化成功！'
\echo '========================================'
\echo ''
\echo '默认账户信息:'
\echo '  用户名: admin'
\echo '  密码: admin123'
\echo '  角色: ADMIN'
\echo ''
\echo '测试账户信息:'
\echo '  用户名: testuser'
\echo '  密码: admin123'
\echo '  角色: USER'
\echo ''
\echo '已创建的表:'
\echo '  - users (用户表)'
\echo '  - roles (角色表)'
\echo '  - permissions (权限表)'
\echo '  - user_roles (用户角色关联表)'
\echo '  - role_permissions (角色权限关联表)'
\echo '  - audit_logs (审计日志表)'
\echo ''

