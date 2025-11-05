-- ============================================
-- 回滚脚本：删除供应商表和相关权限
-- 版本: 1.0
-- 创建日期: 2024-12-XX
-- 说明: 回滚001_add_suppliers_table.sql的更改
-- 警告: ⚠️ 此脚本会删除suppliers表及其所有数据，请谨慎使用！
-- 适用: PostgreSQL 12+
-- ============================================

-- 开始事务
BEGIN;

-- ============================================
-- 第一步：撤销MANAGER角色的供应商导入权限
-- ============================================
DELETE FROM role_permissions
WHERE role_id IN (SELECT id FROM roles WHERE name = 'MANAGER')
    AND permission_id IN (SELECT id FROM permissions WHERE name = 'supplier:import');

-- ============================================
-- 第二步：删除供应商导入权限
-- ============================================
-- 注意：只有当没有其他角色使用此权限时才删除
-- 如果其他角色也在使用此权限，请手动决定是否删除
DELETE FROM permissions
WHERE name = 'supplier:import'
    AND NOT EXISTS (
        SELECT 1 FROM role_permissions 
        WHERE permission_id = permissions.id
    );

-- ============================================
-- 第三步：删除索引
-- ============================================
DROP INDEX IF EXISTS idx_suppliers_code;

-- ============================================
-- 第四步：删除供应商表
-- ============================================
-- 注意：删除表会同时删除所有相关数据
-- 如果有外键引用此表，请先处理外键约束
DROP TABLE IF EXISTS suppliers CASCADE;

-- ============================================
-- 第五步：验证回滚结果
-- ============================================
DO $$
DECLARE
    table_exists BOOLEAN;
    permission_exists BOOLEAN;
    index_exists BOOLEAN;
BEGIN
    -- 检查表是否已删除
    SELECT EXISTS (
        SELECT 1 FROM information_schema.tables 
        WHERE table_schema = 'public' 
        AND table_name = 'suppliers'
    ) INTO table_exists;
    
    -- 检查权限是否已删除
    SELECT EXISTS (
        SELECT 1 FROM permissions WHERE name = 'supplier:import'
    ) INTO permission_exists;
    
    -- 检查索引是否已删除
    SELECT EXISTS (
        SELECT 1 FROM pg_indexes 
        WHERE tablename = 'suppliers' 
        AND indexname = 'idx_suppliers_code'
    ) INTO index_exists;
    
    -- 输出验证结果
    RAISE NOTICE '========================================';
    RAISE NOTICE '回滚脚本执行结果：';
    RAISE NOTICE '  供应商表已删除: %', NOT table_exists;
    RAISE NOTICE '  供应商导入权限已删除: %', NOT permission_exists;
    RAISE NOTICE '  供应商编码索引已删除: %', NOT index_exists;
    RAISE NOTICE '========================================';
END $$;

-- 提交事务
COMMIT;

-- ============================================
-- 回滚完成提示
-- ============================================
\echo ''
\echo '========================================'
\echo '供应商表回滚脚本执行完成！'
\echo '========================================'
\echo ''
\echo '已删除的内容：'
\echo '  - suppliers 表（包括所有数据）'
\echo '  - idx_suppliers_code 索引'
\echo '  - MANAGER 角色的供应商导入权限'
\echo '  - supplier:import 权限（如果没有其他角色使用）'
\echo ''
\echo '警告：所有供应商数据已被删除！'
\echo ''

