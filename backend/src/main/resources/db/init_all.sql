-- ============================================
-- ERP系统数据库完整初始化脚本
-- 版本: 3.1
-- 创建日期: 2024-12-20
-- 更新日期: 2024-12-XX
-- 说明: 一次性完成所有表结构和初始数据的初始化
-- 包含：用户、角色、权限、审计日志、单位、物料等所有表
-- ============================================

-- 开始事务
BEGIN;

-- ============================================
-- 第一步：删除所有现有表（按依赖顺序）
-- ============================================
DROP TABLE IF EXISTS import_task_failures CASCADE;
DROP TABLE IF EXISTS import_task_dependencies CASCADE;
DROP TABLE IF EXISTS import_task_items CASCADE;
DROP TABLE IF EXISTS import_tasks CASCADE;
DROP TABLE IF EXISTS sale_order_items CASCADE;
DROP TABLE IF EXISTS sale_orders CASCADE;
DROP TABLE IF EXISTS sale_outstock_items CASCADE;
DROP TABLE IF EXISTS sale_outstocks CASCADE;
DROP TABLE IF EXISTS customers CASCADE;
DROP TABLE IF EXISTS suppliers CASCADE;
-- 删除采购订单相关表
DROP TABLE IF EXISTS purchase_order_items CASCADE;
DROP TABLE IF EXISTS purchase_orders CASCADE;
-- 删除委外订单相关表
DROP TABLE IF EXISTS sub_req_order_items CASCADE;
DROP TABLE IF EXISTS sub_req_orders CASCADE;
DROP TYPE IF EXISTS purchase_order_status;
DROP TYPE IF EXISTS sale_order_item_status;
DROP TYPE IF EXISTS sale_order_status;
DROP TYPE IF EXISTS sub_req_order_status;

-- 删除BOM相关表
DROP TABLE IF EXISTS bom_items CASCADE;
DROP TABLE IF EXISTS bill_of_materials CASCADE;
-- 删除物料相关表
DROP TABLE IF EXISTS materials CASCADE;
DROP TABLE IF EXISTS material_groups CASCADE;
-- 删除单位相关表
DROP TABLE IF EXISTS unit_conversions CASCADE;
DROP TABLE IF EXISTS units CASCADE;
DROP TABLE IF EXISTS unit_groups CASCADE;
-- 删除审计和用户相关表
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

-- 导入任务主表
CREATE TABLE import_tasks (
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

-- 导入任务子项表
CREATE TABLE import_task_items (
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

-- 导入任务依赖表
CREATE TABLE import_task_dependencies (
    id BIGSERIAL PRIMARY KEY,
    task_id BIGINT NOT NULL REFERENCES import_tasks(id) ON DELETE CASCADE,
    depends_on_id BIGINT NOT NULL REFERENCES import_tasks(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_import_task_dependencies_self CHECK (task_id <> depends_on_id)
);

-- 导入失败记录表
CREATE TABLE import_task_failures (
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

-- 单位组表
CREATE TABLE unit_groups (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(200),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 单位表
CREATE TABLE units (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    unit_group_id BIGINT NOT NULL REFERENCES unit_groups(id) ON DELETE CASCADE,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 单位转换表
CREATE TABLE unit_conversions (
    id BIGSERIAL PRIMARY KEY,
    from_unit_id BIGINT NOT NULL REFERENCES units(id) ON DELETE CASCADE,
    to_unit_id BIGINT NOT NULL REFERENCES units(id) ON DELETE CASCADE,
    convert_type VARCHAR(20) NOT NULL DEFAULT 'FIXED',
    numerator DECIMAL(18, 6) NOT NULL,
    denominator DECIMAL(18, 6) NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_unit_conversion_self CHECK (from_unit_id != to_unit_id),
    CONSTRAINT chk_unit_conversion_denominator CHECK (denominator > 0)
);

-- 物料组表
CREATE TABLE material_groups (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name TEXT NOT NULL,
    description VARCHAR(200),
    parent_id BIGINT REFERENCES material_groups(id) ON DELETE SET NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 物料表
CREATE TABLE materials (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name TEXT NOT NULL,
    specification TEXT,
    mnemonic_code TEXT,
    old_number VARCHAR(50),
    description TEXT,
    erp_cls_id VARCHAR(50),
    material_group_id BIGINT NOT NULL REFERENCES material_groups(id) ON DELETE RESTRICT,
    base_unit_id BIGINT NOT NULL REFERENCES units(id) ON DELETE RESTRICT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- BOM（物料清单）头表
CREATE TABLE bill_of_materials (
    id BIGSERIAL PRIMARY KEY,
    material_id BIGINT NOT NULL REFERENCES materials(id) ON DELETE CASCADE,
    version VARCHAR(50) NOT NULL DEFAULT 'V000',
    name VARCHAR(200),
    category VARCHAR(100),
    usage VARCHAR(100),
    description TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_bom_material_version UNIQUE (material_id, version)
);

-- BOM（物料清单）明细表
CREATE TABLE bom_items (
    id BIGSERIAL PRIMARY KEY,
    bom_id BIGINT NOT NULL REFERENCES bill_of_materials(id) ON DELETE CASCADE,
    sequence INTEGER NOT NULL,
    child_material_id BIGINT NOT NULL REFERENCES materials(id) ON DELETE RESTRICT,
    child_unit_id BIGINT NOT NULL REFERENCES units(id) ON DELETE RESTRICT,
    numerator DECIMAL(18, 6) NOT NULL DEFAULT 1,
    denominator DECIMAL(18, 6) NOT NULL DEFAULT 1,
    scrap_rate DECIMAL(5, 2),
    child_bom_version VARCHAR(50),
    memo TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_bom_item_denominator CHECK (denominator > 0),
    CONSTRAINT chk_bom_item_sequence CHECK (sequence > 0)
);

-- 客户表
CREATE TABLE customers (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 供应商表
CREATE TABLE suppliers (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name TEXT NOT NULL,
    short_name TEXT,
    english_name TEXT,
    description TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TYPE purchase_order_status AS ENUM ('OPEN', 'CLOSED');

-- 销售订单状态类型
CREATE TYPE sale_order_status AS ENUM ('OPEN', 'CLOSED');
CREATE TYPE sale_order_item_status AS ENUM ('OPEN', 'CLOSED');

-- 委外订单状态类型
CREATE TYPE sub_req_order_status AS ENUM ('OPEN', 'CLOSED');

-- 采购订单主表
CREATE TABLE purchase_orders (
    id BIGSERIAL PRIMARY KEY,
    bill_no VARCHAR(100) NOT NULL UNIQUE,
    order_date DATE NOT NULL,
    supplier_id BIGINT NOT NULL REFERENCES suppliers(id) ON DELETE RESTRICT,
    status purchase_order_status NOT NULL DEFAULT 'OPEN',
    note TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 采购订单明细表
CREATE TABLE purchase_order_items (
    id BIGSERIAL PRIMARY KEY,
    purchase_order_id BIGINT NOT NULL REFERENCES purchase_orders(id) ON DELETE CASCADE,
    sequence INTEGER NOT NULL,
    material_id BIGINT NOT NULL REFERENCES materials(id) ON DELETE RESTRICT,
    bom_id BIGINT REFERENCES bill_of_materials(id) ON DELETE SET NULL,
    material_desc TEXT,
    unit_id BIGINT NOT NULL REFERENCES units(id) ON DELETE RESTRICT,
    qty DECIMAL(18, 6) NOT NULL,
    plan_confirm BOOLEAN DEFAULT FALSE,
    sal_unit_id BIGINT REFERENCES units(id) ON DELETE SET NULL,
    sal_qty DECIMAL(18, 6),
    sal_join_qty DECIMAL(18, 6),
    base_sal_join_qty DECIMAL(18, 6),
    remarks TEXT,
    sal_base_qty DECIMAL(18, 6),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_purchase_order_item_sequence CHECK (sequence > 0),
    CONSTRAINT chk_purchase_order_item_qty CHECK (qty > 0)
);

-- 销售订单表
CREATE TABLE sale_orders (
    id BIGSERIAL PRIMARY KEY,
    bill_no VARCHAR(100) NOT NULL UNIQUE,
    order_date DATE NOT NULL,
    note TEXT,
    wo_number VARCHAR(100),
    customer_id BIGINT REFERENCES customers(id) ON DELETE RESTRICT,
    status sale_order_status NOT NULL DEFAULT 'OPEN',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 销售订单明细表
CREATE TABLE sale_order_items (
    id BIGSERIAL PRIMARY KEY,
    sale_order_id BIGINT NOT NULL REFERENCES sale_orders(id) ON DELETE CASCADE,
    sequence INTEGER NOT NULL,
    material_id BIGINT NOT NULL REFERENCES materials(id) ON DELETE RESTRICT,
    unit_id BIGINT NOT NULL REFERENCES units(id) ON DELETE RESTRICT,
    qty DECIMAL(18, 6) NOT NULL,
    old_qty DECIMAL(18, 6),
    inspection_date DATE,
    delivery_date TIMESTAMP,
    bom_version VARCHAR(50),
    entry_note TEXT,
    customer_order_no VARCHAR(100),
    customer_line_no VARCHAR(50),
    status sale_order_item_status NOT NULL DEFAULT 'OPEN',
    delivered_qty DECIMAL(18, 6) NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_sale_order_item_qty CHECK (qty > 0),
    CONSTRAINT chk_sale_order_item_sequence CHECK (sequence > 0),
    CONSTRAINT chk_sale_order_item_delivered_qty CHECK (delivered_qty >= 0)
);

ALTER TABLE sale_order_items
    ADD CONSTRAINT uq_sale_order_items_sequence UNIQUE (sequence);

-- 销售出库主表
CREATE TABLE sale_outstocks (
    id BIGSERIAL PRIMARY KEY,
    bill_no VARCHAR(100) NOT NULL UNIQUE,
    outstock_date DATE NOT NULL,
    note TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 销售出库明细表
CREATE TABLE sale_outstock_items (
    id BIGSERIAL PRIMARY KEY,
    sale_outstock_id BIGINT NOT NULL REFERENCES sale_outstocks(id) ON DELETE CASCADE,
    sequence INTEGER NOT NULL,
    sale_order_item_id INTEGER NOT NULL REFERENCES sale_order_items(id) ON DELETE RESTRICT,
    material_id BIGINT NOT NULL REFERENCES materials(id) ON DELETE RESTRICT,
    unit_id BIGINT NOT NULL REFERENCES units(id) ON DELETE RESTRICT,
    qty DECIMAL(18, 6) NOT NULL,
    entry_note TEXT,
    wo_number VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_sale_outstock_item_sequence CHECK (sequence > 0),
    CONSTRAINT chk_sale_outstock_item_qty CHECK (qty > 0)
);

-- 委外订单主表
CREATE TABLE sub_req_orders (
    id BIGSERIAL PRIMARY KEY,
    bill_head_seq INTEGER NOT NULL,
    description TEXT,
    status sub_req_order_status NOT NULL DEFAULT 'OPEN',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 委外订单明细表
CREATE TABLE sub_req_order_items (
    id BIGSERIAL PRIMARY KEY,
    sub_req_order_id BIGINT NOT NULL REFERENCES sub_req_orders(id) ON DELETE CASCADE,
    sequence INTEGER NOT NULL,
    material_id BIGINT NOT NULL REFERENCES materials(id) ON DELETE RESTRICT,
    unit_id BIGINT NOT NULL REFERENCES units(id) ON DELETE RESTRICT,
    qty DECIMAL(18, 6) NOT NULL,
    bom_id BIGINT REFERENCES bill_of_materials(id) ON DELETE SET NULL,
    supplier_id BIGINT NOT NULL REFERENCES suppliers(id) ON DELETE RESTRICT,
    lot_master VARCHAR(200),
    lot_manual VARCHAR(200),
    base_no_stock_in_qty DECIMAL(18, 6),
    no_stock_in_qty DECIMAL(18, 6),
    pick_mtrl_status VARCHAR(50),
    description TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_sub_req_order_item_sequence CHECK (sequence > 0),
    CONSTRAINT chk_sub_req_order_item_qty CHECK (qty > 0)
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

-- 导入任务相关索引
CREATE INDEX idx_import_tasks_type ON import_tasks(import_type);
CREATE INDEX idx_import_tasks_status ON import_tasks(status);
CREATE INDEX idx_import_tasks_created_at ON import_tasks(created_at DESC);

CREATE INDEX idx_import_task_items_task_id ON import_task_items(task_id);
CREATE INDEX idx_import_task_items_status ON import_task_items(status);
CREATE INDEX idx_import_task_items_retry ON import_task_items(retry_of_item_id);

CREATE INDEX idx_import_task_dependencies_task_id ON import_task_dependencies(task_id);
CREATE INDEX idx_import_task_dependencies_depends_on ON import_task_dependencies(depends_on_id);

CREATE INDEX idx_import_task_failures_task_id ON import_task_failures(task_id);
CREATE INDEX idx_import_task_failures_task_item_id ON import_task_failures(task_item_id);
CREATE INDEX idx_import_task_failures_status ON import_task_failures(status);

-- 单位组表索引
CREATE INDEX idx_unit_groups_code ON unit_groups(code);

-- 单位表索引
CREATE INDEX idx_units_code ON units(code);
CREATE INDEX idx_units_unit_group_id ON units(unit_group_id);
CREATE INDEX idx_units_enabled ON units(enabled);

-- 单位转换表索引
CREATE INDEX idx_unit_conversions_from_unit_id ON unit_conversions(from_unit_id);
CREATE INDEX idx_unit_conversions_to_unit_id ON unit_conversions(to_unit_id);

-- 物料组表索引
CREATE INDEX idx_material_groups_code ON material_groups(code);
CREATE INDEX idx_material_groups_parent_id ON material_groups(parent_id);

-- 物料表索引
CREATE INDEX idx_materials_code ON materials(code);
CREATE INDEX idx_materials_material_group_id ON materials(material_group_id);
CREATE INDEX idx_materials_base_unit_id ON materials(base_unit_id);

-- BOM表索引
CREATE INDEX idx_bill_of_materials_material_id ON bill_of_materials(material_id);
CREATE INDEX idx_bill_of_materials_version ON bill_of_materials(version);
CREATE INDEX idx_bom_items_bom_id ON bom_items(bom_id);
CREATE INDEX idx_bom_items_child_material_id ON bom_items(child_material_id);
CREATE INDEX idx_bom_items_child_unit_id ON bom_items(child_unit_id);
CREATE INDEX idx_bom_items_sequence ON bom_items(bom_id, sequence);

-- 客户表索引
CREATE INDEX idx_customers_code ON customers(code);

-- 供应商表索引
CREATE INDEX idx_suppliers_code ON suppliers(code);

-- 采购订单主表索引
CREATE INDEX idx_purchase_orders_bill_no ON purchase_orders(bill_no);
CREATE INDEX idx_purchase_orders_supplier_id ON purchase_orders(supplier_id);
CREATE INDEX idx_purchase_orders_order_date ON purchase_orders(order_date);
CREATE INDEX idx_purchase_orders_status ON purchase_orders(status);

-- 采购订单明细表索引
CREATE INDEX idx_purchase_order_items_order_id ON purchase_order_items(purchase_order_id);
CREATE INDEX idx_purchase_order_items_material_id ON purchase_order_items(material_id);

-- 销售订单表索引
CREATE INDEX idx_sale_orders_bill_no ON sale_orders(bill_no);
CREATE INDEX idx_sale_orders_customer_id ON sale_orders(customer_id);
CREATE INDEX idx_sale_orders_order_date ON sale_orders(order_date);
CREATE INDEX idx_sale_orders_status ON sale_orders(status);

-- 销售订单明细表索引
CREATE INDEX idx_sale_order_items_sale_order_id ON sale_order_items(sale_order_id);
CREATE INDEX idx_sale_order_items_material_id ON sale_order_items(material_id);
CREATE INDEX idx_sale_order_items_unit_id ON sale_order_items(unit_id);
CREATE INDEX idx_sale_order_items_sequence ON sale_order_items(sale_order_id, sequence);
CREATE INDEX idx_sale_order_items_status ON sale_order_items(status);

-- 销售出库表索引
CREATE INDEX idx_sale_outstocks_bill_no ON sale_outstocks(bill_no);
CREATE INDEX idx_sale_outstocks_outstock_date ON sale_outstocks(outstock_date);

CREATE INDEX idx_sale_outstock_items_outstock_id ON sale_outstock_items(sale_outstock_id);
CREATE INDEX idx_sale_outstock_items_order_item_id ON sale_outstock_items(sale_order_item_id);
CREATE INDEX idx_sale_outstock_items_material_id ON sale_outstock_items(material_id);

-- 委外订单主表索引
CREATE INDEX idx_sub_req_orders_bill_head_seq ON sub_req_orders(bill_head_seq);
CREATE INDEX idx_sub_req_orders_status ON sub_req_orders(status);

-- 委外订单明细表索引
CREATE INDEX idx_sub_req_order_items_order_id ON sub_req_order_items(sub_req_order_id);
CREATE INDEX idx_sub_req_order_items_material_id ON sub_req_order_items(material_id);
CREATE INDEX idx_sub_req_order_items_supplier_id ON sub_req_order_items(supplier_id);
CREATE INDEX idx_sub_req_order_items_sequence ON sub_req_order_items(sub_req_order_id, sequence);

-- ============================================
-- 第四步：添加表注释和列注释
-- ============================================

COMMENT ON TABLE users IS '用户表';
COMMENT ON TABLE roles IS '角色表';
COMMENT ON TABLE permissions IS '权限表';
COMMENT ON TABLE user_roles IS '用户角色关联表';
COMMENT ON TABLE role_permissions IS '角色权限关联表';
COMMENT ON TABLE audit_logs IS '审计日志表';
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

COMMENT ON TABLE unit_groups IS '单位组表';
COMMENT ON TABLE units IS '单位表';
COMMENT ON TABLE unit_conversions IS '单位转换表';
COMMENT ON TABLE material_groups IS '物料组表';
COMMENT ON TABLE materials IS '物料表';
COMMENT ON TABLE customers IS '客户表';
COMMENT ON TABLE suppliers IS '供应商表';
COMMENT ON TABLE sale_orders IS '销售订单表';
COMMENT ON TABLE sale_order_items IS '销售订单明细表';
COMMENT ON TABLE purchase_orders IS '采购订单主表';
COMMENT ON COLUMN purchase_orders.id IS '采购订单ID';
COMMENT ON COLUMN purchase_orders.bill_no IS '单据编号（唯一）';
COMMENT ON COLUMN purchase_orders.order_date IS '采购日期';
COMMENT ON COLUMN purchase_orders.supplier_id IS '供应商ID';
COMMENT ON COLUMN purchase_orders.status IS '订单状态：OPEN-进行中，CLOSED-已关闭';
COMMENT ON COLUMN purchase_orders.note IS '备注';
COMMENT ON COLUMN purchase_orders.created_at IS '创建时间';
COMMENT ON COLUMN purchase_orders.updated_at IS '更新时间';

COMMENT ON TABLE purchase_order_items IS '采购订单明细表';
COMMENT ON COLUMN purchase_order_items.id IS '采购订单明细ID';
COMMENT ON COLUMN purchase_order_items.purchase_order_id IS '采购订单ID';
COMMENT ON COLUMN purchase_order_items.sequence IS '序号';
COMMENT ON COLUMN purchase_order_items.material_id IS '物料ID';
COMMENT ON COLUMN purchase_order_items.bom_id IS 'BOM版本ID';
COMMENT ON COLUMN purchase_order_items.material_desc IS '物料说明';
COMMENT ON COLUMN purchase_order_items.unit_id IS '采购单位ID';
COMMENT ON COLUMN purchase_order_items.qty IS '采购数量';
COMMENT ON COLUMN purchase_order_items.plan_confirm IS '计划确认标识';
COMMENT ON COLUMN purchase_order_items.sal_unit_id IS '销售单位ID';
COMMENT ON COLUMN purchase_order_items.sal_qty IS '销售数量';
COMMENT ON COLUMN purchase_order_items.sal_join_qty IS '销售订单关联数量';
COMMENT ON COLUMN purchase_order_items.base_sal_join_qty IS '销售订单关联基本数量';
COMMENT ON COLUMN purchase_order_items.remarks IS '备注';
COMMENT ON COLUMN purchase_order_items.sal_base_qty IS '销售基本数量';
COMMENT ON COLUMN purchase_order_items.created_at IS '创建时间';
COMMENT ON COLUMN purchase_order_items.updated_at IS '更新时间';

-- 采购订单更新时间触发器函数
CREATE OR REPLACE FUNCTION update_purchase_order_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 为采购订单主表创建触发器
CREATE TRIGGER trigger_purchase_orders_updated_at
    BEFORE UPDATE ON purchase_orders
    FOR EACH ROW
    EXECUTE FUNCTION update_purchase_order_updated_at();

-- 为采购订单明细表创建触发器
CREATE TRIGGER trigger_purchase_order_items_updated_at
    BEFORE UPDATE ON purchase_order_items
    FOR EACH ROW
    EXECUTE FUNCTION update_purchase_order_updated_at();

-- 为委外订单主表创建触发器
CREATE TRIGGER trigger_sub_req_orders_updated_at
    BEFORE UPDATE ON sub_req_orders
    FOR EACH ROW
    EXECUTE FUNCTION update_purchase_order_updated_at();

-- 为委外订单明细表创建触发器
CREATE TRIGGER trigger_sub_req_order_items_updated_at
    BEFORE UPDATE ON sub_req_order_items
    FOR EACH ROW
    EXECUTE FUNCTION update_purchase_order_updated_at();

COMMENT ON COLUMN sale_orders.id IS '销售订单ID';
COMMENT ON COLUMN sale_orders.bill_no IS '单据编号（唯一）';
COMMENT ON COLUMN sale_orders.order_date IS '订单日期';
COMMENT ON COLUMN sale_orders.note IS '备注';
COMMENT ON COLUMN sale_orders.wo_number IS '工单号';
COMMENT ON COLUMN sale_orders.customer_id IS '客户ID';
COMMENT ON COLUMN sale_orders.status IS '销售订单状态：OPEN-进行中，CLOSED-已关闭';
COMMENT ON COLUMN sale_orders.created_at IS '创建时间';
COMMENT ON COLUMN sale_orders.updated_at IS '更新时间';

COMMENT ON COLUMN sale_order_items.id IS '销售订单明细ID';
COMMENT ON COLUMN sale_order_items.sale_order_id IS '销售订单ID';
COMMENT ON COLUMN sale_order_items.sequence IS '序号';
COMMENT ON COLUMN sale_order_items.material_id IS '物料ID';
COMMENT ON COLUMN sale_order_items.unit_id IS '单位ID';
COMMENT ON COLUMN sale_order_items.qty IS '销售数量';
COMMENT ON COLUMN sale_order_items.old_qty IS '原数量';
COMMENT ON COLUMN sale_order_items.inspection_date IS '验货日期';
COMMENT ON COLUMN sale_order_items.delivery_date IS '要货日期';
COMMENT ON COLUMN sale_order_items.bom_version IS 'BOM版本';
COMMENT ON COLUMN sale_order_items.entry_note IS '备注';
COMMENT ON COLUMN sale_order_items.customer_order_no IS '客户订单号';
COMMENT ON COLUMN sale_order_items.customer_line_no IS '客户行号';
COMMENT ON COLUMN sale_order_items.status IS '销售订单明细状态：OPEN-进行中，CLOSED-已关闭';
COMMENT ON COLUMN sale_order_items.delivered_qty IS '销售订单明细累计出库数量';
COMMENT ON COLUMN sale_order_items.created_at IS '创建时间';
COMMENT ON COLUMN sale_order_items.updated_at IS '更新时间';

COMMENT ON TABLE sale_outstocks IS '销售出库主表';
COMMENT ON COLUMN sale_outstocks.id IS '销售出库ID';
COMMENT ON COLUMN sale_outstocks.bill_no IS '销售出库单据编号（唯一）';
COMMENT ON COLUMN sale_outstocks.outstock_date IS '出库日期';
COMMENT ON COLUMN sale_outstocks.note IS '出库备注';
COMMENT ON COLUMN sale_outstocks.created_at IS '创建时间';
COMMENT ON COLUMN sale_outstocks.updated_at IS '更新时间';

COMMENT ON TABLE sale_outstock_items IS '销售出库明细表';
COMMENT ON COLUMN sale_outstock_items.id IS '销售出库明细ID';
COMMENT ON COLUMN sale_outstock_items.sale_outstock_id IS '销售出库ID';
COMMENT ON COLUMN sale_outstock_items.sequence IS '明细序号';
COMMENT ON COLUMN sale_outstock_items.sale_order_item_id IS '关联的销售订单明细';
COMMENT ON COLUMN sale_outstock_items.material_id IS '物料ID';
COMMENT ON COLUMN sale_outstock_items.unit_id IS '出库单位ID';
COMMENT ON COLUMN sale_outstock_items.qty IS '实发数量';
COMMENT ON COLUMN sale_outstock_items.entry_note IS '明细备注';
COMMENT ON COLUMN sale_outstock_items.wo_number IS '本司WO编号';

COMMENT ON TABLE sub_req_orders IS '委外订单主表';
COMMENT ON COLUMN sub_req_orders.id IS '委外订单ID';
COMMENT ON COLUMN sub_req_orders.bill_head_seq IS '单据头序号';
COMMENT ON COLUMN sub_req_orders.description IS '备注';
COMMENT ON COLUMN sub_req_orders.status IS '订单状态：OPEN-进行中，CLOSED-已关闭';
COMMENT ON COLUMN sub_req_orders.created_at IS '创建时间';
COMMENT ON COLUMN sub_req_orders.updated_at IS '更新时间';

COMMENT ON TABLE sub_req_order_items IS '委外订单明细表';
COMMENT ON COLUMN sub_req_order_items.id IS '委外订单明细ID';
COMMENT ON COLUMN sub_req_order_items.sub_req_order_id IS '委外订单ID';
COMMENT ON COLUMN sub_req_order_items.sequence IS '序号';
COMMENT ON COLUMN sub_req_order_items.material_id IS '物料ID';
COMMENT ON COLUMN sub_req_order_items.unit_id IS '单位ID';
COMMENT ON COLUMN sub_req_order_items.qty IS '数量';
COMMENT ON COLUMN sub_req_order_items.bom_id IS 'BOM版本ID';
COMMENT ON COLUMN sub_req_order_items.supplier_id IS '供应商ID';
COMMENT ON COLUMN sub_req_order_items.lot_master IS '批号主档';
COMMENT ON COLUMN sub_req_order_items.lot_manual IS '批号手工';
COMMENT ON COLUMN sub_req_order_items.base_no_stock_in_qty IS '基本单位未入库数量';
COMMENT ON COLUMN sub_req_order_items.no_stock_in_qty IS '未入库数量';
COMMENT ON COLUMN sub_req_order_items.pick_mtrl_status IS '领料状态';
COMMENT ON COLUMN sub_req_order_items.description IS '备注';
COMMENT ON COLUMN sub_req_order_items.created_at IS '创建时间';
COMMENT ON COLUMN sub_req_order_items.updated_at IS '更新时间';

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
-- 销售订单权限
('sale_order:read', '查看销售订单'),
('sale_order:import', '导入销售订单'),
('sale_outstock:read', '查看销售出库单'),
('sale_outstock:import', '导入销售出库单'),
-- 采购订单权限
('purchase_order:read', '查看采购订单'),
('purchase_order:import', '导入采购订单'),
('purchase_order:update', '更新采购订单'),
-- 委外订单权限
('sub_req_order:read', '查看委外订单'),
('sub_req_order:import', '导入委外订单'),
-- 供应商权限
('supplier:import', '导入供应商'),
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
        'order:read', 'order:write', 'order:delete', 
        'sale_order:read', 'sale_order:import', 'sale_outstock:read', 'sale_outstock:import',
        'purchase_order:read', 'purchase_order:import', 'purchase_order:update',
        'sub_req_order:read', 'sub_req_order:import',
        'supplier:import',
        'system:read'
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
SELECT '审计日志表', COUNT(*) FROM audit_logs
UNION ALL
SELECT '导入任务表', COUNT(*) FROM import_tasks
UNION ALL
SELECT '导入任务子项表', COUNT(*) FROM import_task_items
UNION ALL
SELECT '导入任务依赖表', COUNT(*) FROM import_task_dependencies
UNION ALL
SELECT '导入任务失败表', COUNT(*) FROM import_task_failures
UNION ALL
SELECT '单位组表', COUNT(*) FROM unit_groups
UNION ALL
SELECT '单位表', COUNT(*) FROM units
UNION ALL
SELECT '单位转换表', COUNT(*) FROM unit_conversions
UNION ALL
SELECT '物料组表', COUNT(*) FROM material_groups
UNION ALL
SELECT '物料表', COUNT(*) FROM materials
UNION ALL
SELECT '客户表', COUNT(*) FROM customers
UNION ALL
SELECT '供应商表', COUNT(*) FROM suppliers
UNION ALL
SELECT '销售订单表', COUNT(*) FROM sale_orders
UNION ALL
SELECT '销售订单明细表', COUNT(*) FROM sale_order_items
UNION ALL
SELECT '销售出库表', COUNT(*) FROM sale_outstocks
UNION ALL
SELECT '销售出库明细表', COUNT(*) FROM sale_outstock_items
UNION ALL
SELECT '委外订单表', COUNT(*) FROM sub_req_orders
UNION ALL
SELECT '委外订单明细表', COUNT(*) FROM sub_req_order_items;

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
\echo '  - import_tasks (导入任务主表)'
\echo '  - import_task_items (导入任务子项表)'
\echo '  - import_task_dependencies (导入任务依赖表)'
\echo '  - import_task_failures (导入失败记录表)'
\echo '  - unit_groups (单位组表)'
\echo '  - units (单位表)'
\echo '  - unit_conversions (单位转换表)'
\echo '  - material_groups (物料组表)'
\echo '  - materials (物料表)'
\echo '  - customers (客户表)'
\echo '  - suppliers (供应商表)'
\echo '  - sale_orders (销售订单表)'
\echo '  - sale_order_items (销售订单明细表)'
\echo '  - sale_outstocks (销售出库主表)'
\echo '  - sale_outstock_items (销售出库明细表)'
\echo '  - purchase_orders (采购订单表)'
\echo '  - purchase_order_items (采购订单明细表)'
\echo '  - sub_req_orders (委外订单表)'
\echo '  - sub_req_order_items (委外订单明细表)'
\echo ''

