-- ============================================
-- ERP系统初始数据脚本
-- 版本: 1.0
-- 说明: 插入默认权限、角色和管理员用户
-- ============================================

-- ============================================
-- 插入权限数据
-- ============================================
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
('system:delete', '删除系统设置')
ON CONFLICT (name) DO NOTHING;

-- ============================================
-- 插入角色数据
-- ============================================
INSERT INTO roles (name, description) VALUES
('ADMIN', '系统管理员，拥有所有权限'),
('USER', '普通用户，拥有基本的查看权限'),
('MANAGER', '经理，拥有产品和订单的管理权限')
ON CONFLICT (name) DO NOTHING;

-- ============================================
-- 为ADMIN角色分配所有权限
-- ============================================
INSERT INTO role_permissions (role_id, permission_id)
SELECT 
    r.id,
    p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'ADMIN'
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- ============================================
-- 为USER角色分配基本查看权限
-- ============================================
INSERT INTO role_permissions (role_id, permission_id)
SELECT 
    r.id,
    p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'USER' 
    AND p.name IN ('user:read', 'product:read', 'order:read')
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- ============================================
-- 为MANAGER角色分配管理权限
-- ============================================
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
    )
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- ============================================
-- 插入默认管理员用户
-- 密码: admin123 (BCrypt加密后的值)
-- 注意: BCrypt每次生成的哈希值都不同，但都可以验证同一密码
-- 此哈希值已验证可以正确验证密码 "admin123"
-- ============================================
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
    '$2a$10$frXVPPQ6Adx7L0RUmd3FNezNyWPzJEaZlR46qEBQnQyjyBdELowiW',  -- admin123 (已验证的哈希值)
    'admin@erp.example.com',
    '系统管理员',
    TRUE,
    TRUE,
    TRUE,
    TRUE
)
ON CONFLICT (username) DO NOTHING;

-- ============================================
-- 为管理员分配ADMIN角色
-- ============================================
INSERT INTO user_roles (user_id, role_id)
SELECT 
    u.id,
    r.id
FROM users u
INNER JOIN roles r ON r.name = 'ADMIN'
WHERE u.username = 'admin'
ON CONFLICT (user_id, role_id) DO NOTHING;

-- 确保admin用户有角色（如果没有，再次尝试分配）
DO $$
DECLARE
    admin_user_id BIGINT;
    admin_role_id BIGINT;
    role_count INT;
BEGIN
    -- 获取admin用户ID
    SELECT id INTO admin_user_id FROM users WHERE username = 'admin' LIMIT 1;
    
    -- 获取ADMIN角色ID
    SELECT id INTO admin_role_id FROM roles WHERE name = 'ADMIN' LIMIT 1;
    
    -- 检查admin用户是否有角色
    SELECT COUNT(*) INTO role_count 
    FROM user_roles 
    WHERE user_id = admin_user_id;
    
    -- 如果admin用户存在、ADMIN角色存在，但admin用户没有角色，则分配
    IF admin_user_id IS NOT NULL AND admin_role_id IS NOT NULL AND role_count = 0 THEN
        INSERT INTO user_roles (user_id, role_id) 
        VALUES (admin_user_id, admin_role_id)
        ON CONFLICT (user_id, role_id) DO NOTHING;
        RAISE NOTICE '已为admin用户分配ADMIN角色';
    ELSIF admin_user_id IS NULL THEN
        RAISE WARNING 'admin用户不存在';
    ELSIF admin_role_id IS NULL THEN
        RAISE WARNING 'ADMIN角色不存在';
    END IF;
END $$;

-- ============================================
-- 插入测试用户（可选）
-- ============================================
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
    '$2a$10$frXVPPQ6Adx7L0RUmd3FNezNyWPzJEaZlR46qEBQnQyjyBdELowiW',  -- admin123 (测试用相同密码)
    'testuser@erp.example.com',
    '测试用户',
    TRUE,
    TRUE,
    TRUE,
    TRUE
)
ON CONFLICT (username) DO NOTHING;

-- 为测试用户分配USER角色
INSERT INTO user_roles (user_id, role_id)
SELECT 
    u.id,
    r.id
FROM users u
INNER JOIN roles r ON r.name = 'USER'
WHERE u.username = 'testuser'
ON CONFLICT (user_id, role_id) DO NOTHING;

-- ============================================
-- 验证数据
-- ============================================
SELECT '初始化完成！' AS status;
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

