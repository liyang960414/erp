-- ============================================
-- 迁移脚本：添加供应商表和相关权限
-- 版本: 1.0
-- 创建日期: 2024-12-XX
-- 说明: 在现有数据库中添加suppliers表、索引、注释和权限
-- 适用: PostgreSQL 12+
-- ============================================

-- 开始事务
BEGIN;

-- ============================================
-- 第一步：创建供应商表
-- ============================================
CREATE TABLE IF NOT EXISTS suppliers (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name TEXT NOT NULL,
    short_name TEXT,
    english_name TEXT,
    description TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ============================================
-- 第二步：创建索引
-- ============================================
-- 供应商编码索引（如果不存在）
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes 
        WHERE tablename = 'suppliers' 
        AND indexname = 'idx_suppliers_code'
    ) THEN
        CREATE INDEX idx_suppliers_code ON suppliers(code);
    END IF;
END $$;

-- ============================================
-- 第三步：添加表注释和列注释
-- ============================================
COMMENT ON TABLE suppliers IS '供应商表';

COMMENT ON COLUMN suppliers.id IS '供应商ID';
COMMENT ON COLUMN suppliers.code IS '供应商编码（唯一）';
COMMENT ON COLUMN suppliers.name IS '供应商名称';
COMMENT ON COLUMN suppliers.short_name IS '简称';
COMMENT ON COLUMN suppliers.english_name IS '英文名称';
COMMENT ON COLUMN suppliers.description IS '描述';
COMMENT ON COLUMN suppliers.created_at IS '创建时间';
COMMENT ON COLUMN suppliers.updated_at IS '更新时间';

-- ============================================
-- 第四步：添加供应商导入权限
-- ============================================
-- 插入权限（如果不存在）
INSERT INTO permissions (name, description)
SELECT 'supplier:import', '导入供应商'
WHERE NOT EXISTS (
    SELECT 1 FROM permissions WHERE name = 'supplier:import'
);

-- ============================================
-- 第五步：为角色分配供应商导入权限
-- ============================================
-- 为ADMIN角色分配supplier:import权限（ADMIN应该拥有所有权限）
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

-- 为MANAGER角色分配supplier:import权限
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'MANAGER' 
    AND p.name = 'supplier:import'
    AND NOT EXISTS (
        SELECT 1 FROM role_permissions rp
        WHERE rp.role_id = r.id AND rp.permission_id = p.id
    );

-- ============================================
-- 第六步：验证数据
-- ============================================
DO $$
DECLARE
    table_exists BOOLEAN;
    permission_exists BOOLEAN;
    index_exists BOOLEAN;
    admin_has_permission BOOLEAN;
    manager_has_permission BOOLEAN;
BEGIN
    -- 检查表是否存在
    SELECT EXISTS (
        SELECT 1 FROM information_schema.tables 
        WHERE table_schema = 'public' 
        AND table_name = 'suppliers'
    ) INTO table_exists;
    
    -- 检查权限是否存在
    SELECT EXISTS (
        SELECT 1 FROM permissions WHERE name = 'supplier:import'
    ) INTO permission_exists;
    
    -- 检查索引是否存在
    SELECT EXISTS (
        SELECT 1 FROM pg_indexes 
        WHERE tablename = 'suppliers' 
        AND indexname = 'idx_suppliers_code'
    ) INTO index_exists;
    
    -- 检查ADMIN角色是否有权限
    SELECT EXISTS (
        SELECT 1 FROM role_permissions rp
        JOIN roles r ON rp.role_id = r.id
        JOIN permissions p ON rp.permission_id = p.id
        WHERE r.name = 'ADMIN' AND p.name = 'supplier:import'
    ) INTO admin_has_permission;
    
    -- 检查MANAGER角色是否有权限
    SELECT EXISTS (
        SELECT 1 FROM role_permissions rp
        JOIN roles r ON rp.role_id = r.id
        JOIN permissions p ON rp.permission_id = p.id
        WHERE r.name = 'MANAGER' AND p.name = 'supplier:import'
    ) INTO manager_has_permission;
    
    -- 输出验证结果
    RAISE NOTICE '========================================';
    RAISE NOTICE '迁移脚本执行结果：';
    RAISE NOTICE '  供应商表存在: %', table_exists;
    RAISE NOTICE '  供应商导入权限存在: %', permission_exists;
    RAISE NOTICE '  供应商编码索引存在: %', index_exists;
    RAISE NOTICE '  ADMIN角色有权限: %', admin_has_permission;
    RAISE NOTICE '  MANAGER角色有权限: %', manager_has_permission;
    RAISE NOTICE '========================================';
END $$;

-- 提交事务
COMMIT;

-- ============================================
-- 迁移完成提示
-- ============================================
\echo ''
\echo '========================================'
\echo '供应商表迁移脚本执行完成！'
\echo '========================================'
\echo ''
\echo '已创建的内容：'
\echo '  - suppliers 表'
\echo '  - idx_suppliers_code 索引'
\echo '  - supplier:import 权限'
\echo '  - ADMIN 角色的供应商导入权限'
\echo '  - MANAGER 角色的供应商导入权限'
\echo ''
\echo '如果表已存在，脚本会安全跳过，不会报错。'
\echo ''

