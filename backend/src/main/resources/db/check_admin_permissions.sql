-- ============================================
-- 检查admin用户权限状态
-- 用于诊断admin用户没有权限的问题
-- ============================================

-- 1. 检查admin用户是否存在
SELECT 
    'admin用户信息' AS 检查项,
    id AS 用户ID,
    username AS 用户名,
    email AS 邮箱,
    enabled AS 已启用,
    account_non_expired AS 账户未过期,
    account_non_locked AS 账户未锁定,
    credentials_non_expired AS 凭证未过期
FROM users 
WHERE username = 'admin';

-- 2. 检查admin用户的角色
SELECT 
    'admin用户角色' AS 检查项,
    u.id AS 用户ID,
    u.username AS 用户名,
    r.id AS 角色ID,
    r.name AS 角色名称,
    r.description AS 角色描述
FROM users u
LEFT JOIN user_roles ur ON u.id = ur.user_id
LEFT JOIN roles r ON ur.role_id = r.id
WHERE u.username = 'admin';

-- 3. 检查ADMIN角色是否存在
SELECT 
    'ADMIN角色信息' AS 检查项,
    id AS 角色ID,
    name AS 角色名称,
    description AS 角色描述
FROM roles 
WHERE name = 'ADMIN';

-- 4. 检查ADMIN角色的权限
SELECT 
    'ADMIN角色权限' AS 检查项,
    r.id AS 角色ID,
    r.name AS 角色名称,
    p.id AS 权限ID,
    p.name AS 权限名称,
    p.description AS 权限描述
FROM roles r
LEFT JOIN role_permissions rp ON r.id = rp.role_id
LEFT JOIN permissions p ON rp.permission_id = p.id
WHERE r.name = 'ADMIN'
ORDER BY p.name;

-- 5. 统计admin用户的权限数量
SELECT 
    '权限统计' AS 检查项,
    u.username AS 用户名,
    COUNT(DISTINCT r.id) AS 角色数量,
    COUNT(DISTINCT p.id) AS 权限数量
FROM users u
LEFT JOIN user_roles ur ON u.id = ur.user_id
LEFT JOIN roles r ON ur.role_id = r.id
LEFT JOIN role_permissions rp ON r.id = rp.role_id
LEFT JOIN permissions p ON rp.permission_id = p.id
WHERE u.username = 'admin'
GROUP BY u.username;

-- 6. 诊断问题（如果admin用户没有角色，显示修复SQL）
SELECT 
    CASE 
        WHEN u.id IS NULL THEN '❌ 问题：admin用户不存在'
        WHEN r.id IS NULL THEN '❌ 问题：admin用户没有分配角色'
        WHEN COUNT(DISTINCT p.id) = 0 THEN '❌ 问题：admin用户的角色没有权限'
        ELSE '✓ admin用户权限正常'
    END AS 诊断结果,
    CASE 
        WHEN u.id IS NULL THEN '请执行 INSERT INTO users ... 创建admin用户'
        WHEN r.id IS NULL THEN '请执行: INSERT INTO user_roles (user_id, role_id) SELECT u.id, r.id FROM users u, roles r WHERE u.username = ''admin'' AND r.name = ''ADMIN'' ON CONFLICT DO NOTHING;'
        WHEN COUNT(DISTINCT p.id) = 0 THEN '请检查ADMIN角色是否分配了权限'
        ELSE '无需修复'
    END AS 修复建议
FROM users u
LEFT JOIN user_roles ur ON u.id = ur.user_id
LEFT JOIN roles r ON ur.role_id = r.id
LEFT JOIN role_permissions rp ON r.id = rp.role_id
LEFT JOIN permissions p ON rp.permission_id = p.id
WHERE u.username = 'admin' OR u.username IS NULL
GROUP BY u.id, r.id
HAVING u.id IS NULL OR r.id IS NULL OR COUNT(DISTINCT p.id) = 0
UNION ALL
SELECT 
    '✓ admin用户权限正常' AS 诊断结果,
    '无需修复' AS 修复建议
WHERE EXISTS (
    SELECT 1 
    FROM users u
    INNER JOIN user_roles ur ON u.id = ur.user_id
    INNER JOIN roles r ON ur.role_id = r.id
    INNER JOIN role_permissions rp ON r.id = rp.role_id
    WHERE u.username = 'admin'
    GROUP BY u.id
    HAVING COUNT(DISTINCT rp.permission_id) > 0
);

