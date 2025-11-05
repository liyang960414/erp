-- ============================================
-- 快速修复脚本：为ADMIN角色添加supplier:import权限
-- 用途: 如果之前执行过旧版本的迁移脚本，使用此脚本为ADMIN角色添加权限
-- ============================================

-- 开始事务
BEGIN;

-- 确保权限存在
INSERT INTO permissions (name, description)
SELECT 'supplier:import', '导入供应商'
WHERE NOT EXISTS (
    SELECT 1 FROM permissions WHERE name = 'supplier:import'
);

-- 为ADMIN角色分配supplier:import权限
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'ADMIN' 
    AND p.name = 'supplier:import'
    AND NOT EXISTS (
        SELECT 1 FROM role_permissions rp
        WHERE rp.role_id = r.id AND rp.permission_id = p.id
    );

-- 验证
DO $$
DECLARE
    admin_has_permission BOOLEAN;
BEGIN
    SELECT EXISTS (
        SELECT 1 FROM role_permissions rp
        JOIN roles r ON rp.role_id = r.id
        JOIN permissions p ON rp.permission_id = p.id
        WHERE r.name = 'ADMIN' AND p.name = 'supplier:import'
    ) INTO admin_has_permission;
    
    IF admin_has_permission THEN
        RAISE NOTICE '✓ ADMIN角色已成功分配supplier:import权限';
    ELSE
        RAISE WARNING '✗ ADMIN角色权限分配失败，请检查';
    END IF;
END $$;

-- 提交事务
COMMIT;

\echo ''
\echo '修复完成！ADMIN角色现在拥有supplier:import权限。'
\echo '请重新登录系统以使权限生效。'
\echo ''

