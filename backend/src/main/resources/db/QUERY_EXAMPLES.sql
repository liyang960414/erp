-- ============================================
-- ERP系统常用查询示例
-- 版本: 1.0
-- 说明: 包含常用的SQL查询语句
-- ============================================

-- ============================================
-- 1. 用户相关查询
-- ============================================

-- 查看所有用户及角色
SELECT 
    u.id,
    u.username,
    u.email,
    u.full_name,
    u.enabled,
    r.name AS role_name,
    r.description AS role_description
FROM users u
LEFT JOIN user_roles ur ON u.id = ur.user_id
LEFT JOIN roles r ON ur.role_id = r.id
ORDER BY u.username;

-- 查看单个用户的所有角色和权限
SELECT 
    u.username,
    r.name AS role_name,
    p.name AS permission_name,
    p.description AS permission_description
FROM users u
JOIN user_roles ur ON u.id = ur.user_id
JOIN roles r ON ur.role_id = r.id
JOIN role_permissions rp ON r.id = rp.role_id
JOIN permissions p ON rp.permission_id = p.id
WHERE u.username = 'admin'
ORDER BY r.name, p.name;

-- 查找拥有特定权限的所有用户
SELECT DISTINCT
    u.username,
    u.email,
    r.name AS role_name
FROM users u
JOIN user_roles ur ON u.id = ur.user_id
JOIN roles r ON ur.role_id = r.id
JOIN role_permissions rp ON r.id = rp.role_id
JOIN permissions p ON rp.permission_id = p.id
WHERE p.name = 'user:write'
ORDER BY u.username;

-- 查看所有启用的用户
SELECT username, email, full_name, created_at
FROM users
WHERE enabled = TRUE
ORDER BY created_at DESC;

-- 统计用户数量
SELECT 
    COUNT(*) AS total_users,
    SUM(CASE WHEN enabled THEN 1 ELSE 0 END) AS enabled_users,
    SUM(CASE WHEN enabled THEN 0 ELSE 1 END) AS disabled_users
FROM users;

-- ============================================
-- 2. 角色相关查询
-- ============================================

-- 查看所有角色及其权限数量
SELECT 
    r.id,
    r.name,
    r.description,
    COUNT(DISTINCT rp.permission_id) AS permission_count
FROM roles r
LEFT JOIN role_permissions rp ON r.id = rp.role_id
GROUP BY r.id, r.name, r.description
ORDER BY r.name;

-- 查看角色的所有权限
SELECT 
    r.name AS role_name,
    p.name AS permission_name,
    p.description AS permission_description
FROM roles r
JOIN role_permissions rp ON r.id = rp.role_id
JOIN permissions p ON rp.permission_id = p.id
WHERE r.name = 'ADMIN'
ORDER BY p.name;

-- 查看哪些角色拥有特定权限
SELECT DISTINCT
    r.name AS role_name,
    r.description AS role_description
FROM roles r
JOIN role_permissions rp ON r.id = rp.role_id
JOIN permissions p ON rp.permission_id = p.id
WHERE p.name = 'user:delete';

-- 统计每个角色的用户数量
SELECT 
    r.name AS role_name,
    COUNT(DISTINCT ur.user_id) AS user_count
FROM roles r
LEFT JOIN user_roles ur ON r.id = ur.role_id
GROUP BY r.name
ORDER BY user_count DESC;

-- ============================================
-- 3. 权限相关查询
-- ============================================

-- 查看所有权限
SELECT 
    id,
    name,
    description,
    created_at
FROM permissions
ORDER BY name;

-- 按模块分类查看权限
SELECT 
    SPLIT_PART(name, ':', 1) AS module,
    COUNT(*) AS permission_count
FROM permissions
GROUP BY SPLIT_PART(name, ':', 1)
ORDER BY module;

-- 查看没有分配给任何角色的权限
SELECT 
    p.id,
    p.name,
    p.description
FROM permissions p
LEFT JOIN role_permissions rp ON p.id = rp.permission_id
WHERE rp.permission_id IS NULL;

-- 查看使用最少的权限
SELECT 
    p.name,
    p.description,
    COUNT(DISTINCT rp.role_id) AS role_count
FROM permissions p
LEFT JOIN role_permissions rp ON p.id = rp.permission_id
GROUP BY p.id, p.name, p.description
ORDER BY role_count ASC;

-- ============================================
-- 4. 权限验证查询
-- ============================================

-- 检查用户是否有特定权限（直接验证）
SELECT 
    CASE 
        WHEN COUNT(*) > 0 THEN '拥有权限'
        ELSE '无权限'
    END AS has_permission
FROM users u
JOIN user_roles ur ON u.id = ur.user_id
JOIN roles r ON ur.role_id = r.id
JOIN role_permissions rp ON r.id = rp.role_id
JOIN permissions p ON rp.permission_id = p.id
WHERE u.username = 'admin' AND p.name = 'user:delete';

-- 列出用户缺少的权限
SELECT 
    p.name AS permission_name
FROM permissions p
WHERE p.name NOT IN (
    SELECT DISTINCT p2.name
    FROM users u
    JOIN user_roles ur ON u.id = ur.user_id
    JOIN roles r ON ur.role_id = r.id
    JOIN role_permissions rp ON r.id = rp.role_id
    JOIN permissions p2 ON rp.permission_id = p2.id
    WHERE u.username = 'testuser'
)
ORDER BY p.name;

-- ============================================
-- 5. 管理操作查询
-- ============================================

-- 为角色添加权限
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'MANAGER' 
    AND p.name = 'system:read'
ON CONFLICT DO NOTHING;

-- 为用户添加角色
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u, roles r
WHERE u.username = 'testuser' 
    AND r.name = 'MANAGER'
ON CONFLICT DO NOTHING;

-- 移除用户的角色
DELETE FROM user_roles
WHERE user_id = (SELECT id FROM users WHERE username = 'testuser')
    AND role_id = (SELECT id FROM roles WHERE name = 'MANAGER');

-- 禁用用户
UPDATE users
SET enabled = FALSE
WHERE username = 'testuser';

-- 解锁用户
UPDATE users
SET account_non_locked = TRUE
WHERE username = 'testuser';

-- 重置用户密码（使用BCrypt加密后的值）
UPDATE users
SET password = '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iwKkg0.i'
WHERE username = 'admin';

-- ============================================
-- 6. 报表查询
-- ============================================

-- 用户权限统计报表
SELECT 
    u.username,
    COUNT(DISTINCT ur.role_id) AS role_count,
    COUNT(DISTINCT p.id) AS permission_count,
    STRING_AGG(DISTINCT r.name, ', ') AS roles
FROM users u
LEFT JOIN user_roles ur ON u.id = ur.user_id
LEFT JOIN roles r ON ur.role_id = r.id
LEFT JOIN role_permissions rp ON r.id = rp.role_id
LEFT JOIN permissions p ON rp.permission_id = p.id
GROUP BY u.id, u.username
ORDER BY permission_count DESC;

-- 角色权限分布报表
SELECT 
    r.name AS role_name,
    COUNT(DISTINCT rp.permission_id) AS total_permissions,
    COUNT(DISTINCT CASE 
        WHEN p.name LIKE '%:read' THEN p.id 
    END) AS read_permissions,
    COUNT(DISTINCT CASE 
        WHEN p.name LIKE '%:write' THEN p.id 
    END) AS write_permissions,
    COUNT(DISTINCT CASE 
        WHEN p.name LIKE '%:delete' THEN p.id 
    END) AS delete_permissions
FROM roles r
LEFT JOIN role_permissions rp ON r.id = rp.role_id
LEFT JOIN permissions p ON rp.permission_id = p.id
GROUP BY r.id, r.name
ORDER BY total_permissions DESC;

-- 按模块分组的权限统计
SELECT 
    SPLIT_PART(p.name, ':', 1) AS module,
    COUNT(DISTINCT p.id) AS total_permissions,
    COUNT(DISTINCT CASE WHEN p.name LIKE '%:read' THEN p.id END) AS read_count,
    COUNT(DISTINCT CASE WHEN p.name LIKE '%:write' THEN p.id END) AS write_count,
    COUNT(DISTINCT CASE WHEN p.name LIKE '%:delete' THEN p.id END) AS delete_count
FROM permissions p
GROUP BY SPLIT_PART(p.name, ':', 1)
ORDER BY module;

-- 最近创建的用户
SELECT 
    username,
    email,
    full_name,
    enabled,
    created_at,
    EXTRACT(DAY FROM CURRENT_TIMESTAMP - created_at) AS days_ago
FROM users
ORDER BY created_at DESC
LIMIT 10;

-- ============================================
-- 7. 数据完整性检查
-- ============================================

-- 检查孤立权限（没有被角色使用）
SELECT 
    '孤立权限' AS issue_type,
    p.name AS object_name
FROM permissions p
LEFT JOIN role_permissions rp ON p.id = rp.permission_id
WHERE rp.permission_id IS NULL;

-- 检查孤立角色（没有被用户使用）
SELECT 
    '孤立角色' AS issue_type,
    r.name AS object_name
FROM roles r
LEFT JOIN user_roles ur ON r.id = ur.role_id
WHERE ur.role_id IS NULL;

-- 检查没有角色的用户
SELECT 
    '无角色用户' AS issue_type,
    u.username AS object_name
FROM users u
LEFT JOIN user_roles ur ON u.id = ur.user_id
WHERE ur.user_id IS NULL;

-- 检查重复的用户角色分配
SELECT 
    user_id,
    role_id,
    COUNT(*) AS duplicate_count
FROM user_roles
GROUP BY user_id, role_id
HAVING COUNT(*) > 1;

-- ============================================
-- 8. 性能优化查询
-- ============================================

-- 查看表大小
SELECT 
    schemaname,
    tablename,
    pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename)) AS size
FROM pg_tables
WHERE schemaname = 'public'
ORDER BY pg_total_relation_size(schemaname||'.'||tablename) DESC;

-- 查看索引使用情况
SELECT 
    schemaname,
    tablename,
    indexname,
    idx_scan AS index_scans,
    pg_size_pretty(pg_relation_size(indexrelid)) AS index_size
FROM pg_stat_user_indexes
WHERE schemaname = 'public'
ORDER BY idx_scan ASC;

-- 分析表以更新统计信息
ANALYZE users;
ANALYZE roles;
ANALYZE permissions;
ANALYZE user_roles;
ANALYZE role_permissions;

-- ============================================
-- 9. 辅助查询
-- ============================================

-- 查看数据库版本
SELECT version();

-- 查看当前连接的数据库
SELECT current_database();

-- 查看所有表
SELECT tablename 
FROM pg_tables 
WHERE schemaname = 'public'
ORDER BY tablename;

-- 查看表结构
SELECT 
    column_name,
    data_type,
    character_maximum_length,
    is_nullable,
    column_default
FROM information_schema.columns
WHERE table_schema = 'public' 
    AND table_name = 'users'
ORDER BY ordinal_position;

-- 查看外键约束
SELECT
    tc.table_name,
    kcu.column_name,
    ccu.table_name AS foreign_table_name,
    ccu.column_name AS foreign_column_name
FROM information_schema.table_constraints AS tc
JOIN information_schema.key_column_usage AS kcu
    ON tc.constraint_name = kcu.constraint_name
JOIN information_schema.constraint_column_usage AS ccu
    ON ccu.constraint_name = tc.constraint_name
WHERE constraint_type = 'FOREIGN KEY'
    AND tc.table_schema = 'public'
ORDER BY tc.table_name, kcu.column_name;

-- ============================================
-- 10. 清理和维护
-- ============================================

-- 清空所有数据但保留表结构
TRUNCATE TABLE user_roles, role_permissions, users, roles, permissions RESTART IDENTITY CASCADE;

-- 重置序列
ALTER SEQUENCE users_id_seq RESTART WITH 1;
ALTER SEQUENCE roles_id_seq RESTART WITH 1;
ALTER SEQUENCE permissions_id_seq RESTART WITH 1;

-- 重新创建索引（如果需要）
REINDEX TABLE users;
REINDEX TABLE roles;
REINDEX TABLE permissions;

-- 更新表统计信息
VACUUM ANALYZE;

-- ============================================
-- 结束
-- ============================================

