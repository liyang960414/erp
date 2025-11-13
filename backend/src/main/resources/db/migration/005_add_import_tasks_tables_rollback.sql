-- ============================================
-- 回滚脚本：删除导入任务相关表
-- 版本: 1.0
-- 创建日期: 2025-11-13
-- 说明: 删除导入任务主表、子项、依赖和失败记录表及相关索引
-- 适用: PostgreSQL 12+
-- ============================================

-- 开始事务
BEGIN;

-- 删除索引（如果存在）
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'idx_import_task_failures_status') THEN
        DROP INDEX idx_import_task_failures_status;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'idx_import_task_failures_task_item_id') THEN
        DROP INDEX idx_import_task_failures_task_item_id;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'idx_import_task_failures_task_id') THEN
        DROP INDEX idx_import_task_failures_task_id;
    END IF;

    IF EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'idx_import_task_dependencies_depends_on') THEN
        DROP INDEX idx_import_task_dependencies_depends_on;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'idx_import_task_dependencies_task_id') THEN
        DROP INDEX idx_import_task_dependencies_task_id;
    END IF;

    IF EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'idx_import_task_items_retry') THEN
        DROP INDEX idx_import_task_items_retry;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'idx_import_task_items_status') THEN
        DROP INDEX idx_import_task_items_status;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'idx_import_task_items_task_id') THEN
        DROP INDEX idx_import_task_items_task_id;
    END IF;

    IF EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'idx_import_tasks_created_at') THEN
        DROP INDEX idx_import_tasks_created_at;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'idx_import_tasks_status') THEN
        DROP INDEX idx_import_tasks_status;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'idx_import_tasks_type') THEN
        DROP INDEX idx_import_tasks_type;
    END IF;
END $$;

-- 按依赖顺序删除表
DROP TABLE IF EXISTS import_task_failures CASCADE;
DROP TABLE IF EXISTS import_task_dependencies CASCADE;
DROP TABLE IF EXISTS import_task_items CASCADE;
DROP TABLE IF EXISTS import_tasks CASCADE;

-- 提交事务
COMMIT;

-- 完成提示
\echo ''
\echo '========================================'
\echo '导入任务相关表回滚脚本执行完成！'
\echo '========================================'
\echo ''
\echo '已删除的内容：'
\echo '  - import_tasks 表及索引'
\echo '  - import_task_items 表及索引'
\echo '  - import_task_dependencies 表及索引'
\echo '  - import_task_failures 表及索引'
\echo ''


