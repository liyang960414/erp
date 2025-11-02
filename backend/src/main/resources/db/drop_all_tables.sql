-- ============================================
-- ERP系统数据库销毁脚本
-- 版本: 1.0
-- 警告: 此脚本将删除所有表和数据
-- 请在执行前备份数据库！
-- ============================================

-- 禁用外键约束检查（PostgreSQL不需要，但保留以便兼容）
-- SET FOREIGN_KEY_CHECKS = 0;

-- ============================================
-- 按顺序删除表（确保外键约束正确）
-- ============================================

-- 删除物料相关表
DROP TABLE IF EXISTS materials CASCADE;
DROP TABLE IF EXISTS material_groups CASCADE;

-- 删除单位相关表
DROP TABLE IF EXISTS unit_conversions CASCADE;
DROP TABLE IF EXISTS units CASCADE;
DROP TABLE IF EXISTS unit_groups CASCADE;

-- 删除关联表
DROP TABLE IF EXISTS user_roles CASCADE;
DROP TABLE IF EXISTS role_permissions CASCADE;

-- 删除主表
DROP TABLE IF EXISTS audit_logs CASCADE;
DROP TABLE IF EXISTS users CASCADE;
DROP TABLE IF EXISTS roles CASCADE;
DROP TABLE IF EXISTS permissions CASCADE;

-- 重新启用外键约束检查
-- SET FOREIGN_KEY_CHECKS = 1;

-- 验证删除
SELECT '所有表已删除！' AS status;

-- 查询剩余表（应该为空）
SELECT 
    tablename 
FROM pg_tables 
WHERE schemaname = 'public'
    AND tablename IN (
        'users', 'roles', 'permissions', 'user_roles', 'role_permissions', 'audit_logs',
        'unit_groups', 'units', 'unit_conversions',
        'material_groups', 'materials'
    );

