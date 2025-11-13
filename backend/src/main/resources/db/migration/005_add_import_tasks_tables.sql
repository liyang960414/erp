-- ============================================
-- 迁移脚本：添加导入任务相关表
-- 版本: 1.0
-- 创建日期: 2025-11-13
-- 说明: 在现有数据库中添加导入任务主表、子项、依赖和失败记录表及索引、注释
-- 适用: PostgreSQL 12+
-- ============================================

-- 开始事务
BEGIN;

-- ============================================
-- 第一步：创建导入任务主表
-- ============================================
CREATE TABLE IF NOT EXISTS import_tasks (
    id BIGSERIAL PRIMARY KEY,
    task_code VARCHAR(64) NOT NULL,
    import_type VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'WAITING',
    created_by VARCHAR(64),
    source_file_name VARCHAR(256),
    options_json TEXT,
    total_count INTEGER NOT NULL DEFAULT 0,
    success_count INTEGER NOT NULL DEFAULT 0,
    failure_count INTEGER NOT NULL DEFAULT 0,
    failure_reason TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    scheduled_at TIMESTAMP,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_import_tasks_task_code UNIQUE (task_code)
);

-- ============================================
-- 第二步：创建导入任务子项表
-- ============================================
CREATE TABLE IF NOT EXISTS import_task_items (
    id BIGSERIAL PRIMARY KEY,
    task_id BIGINT NOT NULL REFERENCES import_tasks(id) ON DELETE CASCADE,
    sequence_no INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    source_file_name VARCHAR(256),
    content_type VARCHAR(128),
    file_content BYTEA,
    payload_json TEXT,
    total_count INTEGER NOT NULL DEFAULT 0,
    success_count INTEGER NOT NULL DEFAULT 0,
    failure_count INTEGER NOT NULL DEFAULT 0,
    failure_reason TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    scheduled_at TIMESTAMP,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    retry_of_item_id BIGINT REFERENCES import_task_items(id) ON DELETE SET NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_import_task_items_sequence CHECK (sequence_no > 0),
    CONSTRAINT uq_import_task_items_sequence UNIQUE (task_id, sequence_no)
);

-- ============================================
-- 第三步：创建导入任务依赖表
-- ============================================
CREATE TABLE IF NOT EXISTS import_task_dependencies (
    id BIGSERIAL PRIMARY KEY,
    task_id BIGINT NOT NULL REFERENCES import_tasks(id) ON DELETE CASCADE,
    depends_on_id BIGINT NOT NULL REFERENCES import_tasks(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_import_task_dependencies_self CHECK (task_id <> depends_on_id)
);

-- ============================================
-- 第四步：创建导入失败记录表
-- ============================================
CREATE TABLE IF NOT EXISTS import_task_failures (
    id BIGSERIAL PRIMARY KEY,
    task_id BIGINT NOT NULL REFERENCES import_tasks(id) ON DELETE CASCADE,
    task_item_id BIGINT REFERENCES import_task_items(id) ON DELETE SET NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    section VARCHAR(128),
    row_number INTEGER,
    field_name VARCHAR(128),
    message TEXT,
    raw_payload TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMP
);

-- ============================================
-- 第五步：创建索引
-- ============================================
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes 
        WHERE tablename = 'import_tasks' 
        AND indexname = 'idx_import_tasks_type'
    ) THEN
        CREATE INDEX idx_import_tasks_type ON import_tasks(import_type);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes 
        WHERE tablename = 'import_tasks' 
        AND indexname = 'idx_import_tasks_status'
    ) THEN
        CREATE INDEX idx_import_tasks_status ON import_tasks(status);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes 
        WHERE tablename = 'import_tasks' 
        AND indexname = 'idx_import_tasks_created_at'
    ) THEN
        CREATE INDEX idx_import_tasks_created_at ON import_tasks(created_at DESC);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes 
        WHERE tablename = 'import_task_items' 
        AND indexname = 'idx_import_task_items_task_id'
    ) THEN
        CREATE INDEX idx_import_task_items_task_id ON import_task_items(task_id);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes 
        WHERE tablename = 'import_task_items' 
        AND indexname = 'idx_import_task_items_status'
    ) THEN
        CREATE INDEX idx_import_task_items_status ON import_task_items(status);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes 
        WHERE tablename = 'import_task_items' 
        AND indexname = 'idx_import_task_items_retry'
    ) THEN
        CREATE INDEX idx_import_task_items_retry ON import_task_items(retry_of_item_id);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes 
        WHERE tablename = 'import_task_dependencies' 
        AND indexname = 'idx_import_task_dependencies_task_id'
    ) THEN
        CREATE INDEX idx_import_task_dependencies_task_id ON import_task_dependencies(task_id);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes 
        WHERE tablename = 'import_task_dependencies' 
        AND indexname = 'idx_import_task_dependencies_depends_on'
    ) THEN
        CREATE INDEX idx_import_task_dependencies_depends_on ON import_task_dependencies(depends_on_id);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes 
        WHERE tablename = 'import_task_failures' 
        AND indexname = 'idx_import_task_failures_task_id'
    ) THEN
        CREATE INDEX idx_import_task_failures_task_id ON import_task_failures(task_id);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes 
        WHERE tablename = 'import_task_failures' 
        AND indexname = 'idx_import_task_failures_task_item_id'
    ) THEN
        CREATE INDEX idx_import_task_failures_task_item_id ON import_task_failures(task_item_id);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes 
        WHERE tablename = 'import_task_failures' 
        AND indexname = 'idx_import_task_failures_status'
    ) THEN
        CREATE INDEX idx_import_task_failures_status ON import_task_failures(status);
    END IF;
END $$;

-- ============================================
-- 第六步：添加表注释和列注释
-- ============================================
COMMENT ON TABLE import_tasks IS '导入任务主表';
COMMENT ON COLUMN import_tasks.task_code IS '业务唯一编号，便于日志追踪';
COMMENT ON COLUMN import_tasks.import_type IS '导入类型（unit、material、bom 等）';
COMMENT ON COLUMN import_tasks.status IS '任务状态：WAITING、RUNNING、SUCCESS、FAILED 等';
COMMENT ON COLUMN import_tasks.options_json IS '额外参数或配置（JSON 字符串）';
COMMENT ON COLUMN import_tasks.total_count IS '待处理总记录数';
COMMENT ON COLUMN import_tasks.success_count IS '处理成功记录数';
COMMENT ON COLUMN import_tasks.failure_count IS '处理失败记录数';
COMMENT ON COLUMN import_tasks.failure_reason IS '任务失败原因描述';

COMMENT ON TABLE import_task_items IS '导入任务子项表';
COMMENT ON COLUMN import_task_items.task_id IS '关联的导入任务ID';
COMMENT ON COLUMN import_task_items.sequence_no IS '执行顺序号';
COMMENT ON COLUMN import_task_items.status IS '任务子项状态：PENDING、PROCESSING、SUCCESS、FAILED 等';
COMMENT ON COLUMN import_task_items.file_content IS '上传文件的二进制内容';
COMMENT ON COLUMN import_task_items.payload_json IS '额外参数或过滤条件（JSON 数据）';
COMMENT ON COLUMN import_task_items.retry_of_item_id IS '重试来源的子项ID';

COMMENT ON TABLE import_task_dependencies IS '导入任务依赖关系表';
COMMENT ON COLUMN import_task_dependencies.task_id IS '当前任务ID';
COMMENT ON COLUMN import_task_dependencies.depends_on_id IS '依赖的前序任务ID';

COMMENT ON TABLE import_task_failures IS '导入失败记录表';
COMMENT ON COLUMN import_task_failures.task_id IS '所属导入任务ID';
COMMENT ON COLUMN import_task_failures.task_item_id IS '所属导入任务子项ID';
COMMENT ON COLUMN import_task_failures.status IS '失败记录状态：PENDING、RESOLVED 等';
COMMENT ON COLUMN import_task_failures.section IS '失败所在模块或分区';
COMMENT ON COLUMN import_task_failures.row_number IS '失败的行号';
COMMENT ON COLUMN import_task_failures.field_name IS '失败的字段名称';
COMMENT ON COLUMN import_task_failures.message IS '失败信息描述';
COMMENT ON COLUMN import_task_failures.raw_payload IS '原始数据（JSON 字符串）';

-- ============================================
-- 第七步：验证创建结果
-- ============================================
DO $$
DECLARE
    tasks_exists BOOLEAN;
    items_exists BOOLEAN;
    dependencies_exists BOOLEAN;
    failures_exists BOOLEAN;
BEGIN
    SELECT EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'public' AND table_name = 'import_tasks'
    ) INTO tasks_exists;

    SELECT EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'public' AND table_name = 'import_task_items'
    ) INTO items_exists;

    SELECT EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'public' AND table_name = 'import_task_dependencies'
    ) INTO dependencies_exists;

    SELECT EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'public' AND table_name = 'import_task_failures'
    ) INTO failures_exists;

    RAISE NOTICE '========================================';
    RAISE NOTICE '迁移脚本执行结果：';
    RAISE NOTICE '  import_tasks 表存在: %', tasks_exists;
    RAISE NOTICE '  import_task_items 表存在: %', items_exists;
    RAISE NOTICE '  import_task_dependencies 表存在: %', dependencies_exists;
    RAISE NOTICE '  import_task_failures 表存在: %', failures_exists;
    RAISE NOTICE '========================================';
END $$;

-- 提交事务
COMMIT;

-- ============================================
-- 迁移完成提示
-- ============================================
\echo ''
\echo '========================================'
\echo '导入任务相关表迁移脚本执行完成！'
\echo '========================================'
\echo ''
\echo '已创建的内容：'
\echo '  - import_tasks 表'
\echo '  - import_task_items 表'
\echo '  - import_task_dependencies 表'
\echo '  - import_task_failures 表'
\echo '  - 相关索引与注释'
\echo ''
\echo '如果表已存在，脚本会安全跳过，不会报错。'
\echo ''


