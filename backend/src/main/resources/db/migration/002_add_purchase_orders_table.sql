-- ============================================
-- 迁移脚本：添加采购订单表和相关权限
-- 版本: 1.0
-- 创建日期: 2024-12-XX
-- 说明: 在现有数据库中添加采购订单相关表、索引、注释和权限
-- 适用: PostgreSQL 12+
-- ============================================

-- 开始事务
BEGIN;

-- ============================================
-- 第一步：创建订单状态枚举类型
-- ============================================
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'purchase_order_status') THEN
        CREATE TYPE purchase_order_status AS ENUM ('OPEN', 'CLOSED');
    END IF;
END $$;

-- ============================================
-- 第二步：创建采购订单主表
-- ============================================
CREATE TABLE IF NOT EXISTS purchase_orders (
    id BIGSERIAL PRIMARY KEY,
    bill_no VARCHAR(100) NOT NULL UNIQUE,
    order_date DATE NOT NULL,
    supplier_id BIGINT NOT NULL REFERENCES suppliers(id) ON DELETE RESTRICT,
    status purchase_order_status NOT NULL DEFAULT 'OPEN',
    note TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ============================================
-- 第三步：创建采购订单明细表
-- ============================================
CREATE TABLE IF NOT EXISTS purchase_order_items (
    id BIGSERIAL PRIMARY KEY,
    purchase_order_id BIGINT NOT NULL REFERENCES purchase_orders(id) ON DELETE CASCADE,
    sequence INTEGER NOT NULL,
    material_id BIGINT NOT NULL REFERENCES materials(id) ON DELETE RESTRICT,
    bom_id BIGINT REFERENCES bill_of_materials(id) ON DELETE SET NULL,
    material_desc TEXT,
    unit_id BIGINT NOT NULL REFERENCES units(id) ON DELETE RESTRICT,
    qty NUMERIC(18, 6) NOT NULL,
    plan_confirm BOOLEAN DEFAULT FALSE,
    sal_unit_id BIGINT REFERENCES units(id) ON DELETE SET NULL,
    sal_qty NUMERIC(18, 6),
    sal_join_qty NUMERIC(18, 6),
    base_sal_join_qty NUMERIC(18, 6),
    remarks TEXT,
    sal_base_qty NUMERIC(18, 6),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ============================================
-- 第四步：创建采购订单交货明细表
-- ============================================
CREATE TABLE IF NOT EXISTS purchase_order_deliveries (
    id BIGSERIAL PRIMARY KEY,
    purchase_order_item_id BIGINT NOT NULL REFERENCES purchase_order_items(id) ON DELETE CASCADE,
    sequence INTEGER NOT NULL,
    delivery_date DATE NOT NULL,
    plan_qty NUMERIC(18, 6) NOT NULL,
    supplier_delivery_date DATE,
    pre_arrival_date DATE,
    transport_lead_time INTEGER,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ============================================
-- 第五步：创建索引
-- ============================================
-- 采购订单主表索引
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes 
        WHERE tablename = 'purchase_orders' 
        AND indexname = 'idx_purchase_orders_bill_no'
    ) THEN
        CREATE INDEX idx_purchase_orders_bill_no ON purchase_orders(bill_no);
    END IF;
    
    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes 
        WHERE tablename = 'purchase_orders' 
        AND indexname = 'idx_purchase_orders_supplier_id'
    ) THEN
        CREATE INDEX idx_purchase_orders_supplier_id ON purchase_orders(supplier_id);
    END IF;
    
    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes 
        WHERE tablename = 'purchase_orders' 
        AND indexname = 'idx_purchase_orders_order_date'
    ) THEN
        CREATE INDEX idx_purchase_orders_order_date ON purchase_orders(order_date);
    END IF;
    
    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes 
        WHERE tablename = 'purchase_orders' 
        AND indexname = 'idx_purchase_orders_status'
    ) THEN
        CREATE INDEX idx_purchase_orders_status ON purchase_orders(status);
    END IF;
END $$;

-- 采购订单明细表索引
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes 
        WHERE tablename = 'purchase_order_items' 
        AND indexname = 'idx_purchase_order_items_order_id'
    ) THEN
        CREATE INDEX idx_purchase_order_items_order_id ON purchase_order_items(purchase_order_id);
    END IF;
    
    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes 
        WHERE tablename = 'purchase_order_items' 
        AND indexname = 'idx_purchase_order_items_material_id'
    ) THEN
        CREATE INDEX idx_purchase_order_items_material_id ON purchase_order_items(material_id);
    END IF;
END $$;

-- 采购订单交货明细表索引
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes 
        WHERE tablename = 'purchase_order_deliveries' 
        AND indexname = 'idx_purchase_order_deliveries_item_id'
    ) THEN
        CREATE INDEX idx_purchase_order_deliveries_item_id ON purchase_order_deliveries(purchase_order_item_id);
    END IF;
    
    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes 
        WHERE tablename = 'purchase_order_deliveries' 
        AND indexname = 'idx_purchase_order_deliveries_delivery_date'
    ) THEN
        CREATE INDEX idx_purchase_order_deliveries_delivery_date ON purchase_order_deliveries(delivery_date);
    END IF;
END $$;

-- ============================================
-- 第六步：添加表注释和列注释
-- ============================================
COMMENT ON TABLE purchase_orders IS '采购订单主表';
COMMENT ON COLUMN purchase_orders.id IS '采购订单ID';
COMMENT ON COLUMN purchase_orders.bill_no IS '单据编号（唯一）';
COMMENT ON COLUMN purchase_orders.order_date IS '采购日期';
COMMENT ON COLUMN purchase_orders.supplier_id IS '供应商ID（供货方就是供应商）';
COMMENT ON COLUMN purchase_orders.status IS '订单状态：OPEN-进行中，CLOSED-已关闭';
COMMENT ON COLUMN purchase_orders.note IS '备注';
COMMENT ON COLUMN purchase_orders.created_at IS '创建时间';
COMMENT ON COLUMN purchase_orders.updated_at IS '更新时间';

COMMENT ON TABLE purchase_order_items IS '采购订单明细表';
COMMENT ON COLUMN purchase_order_items.id IS '采购订单明细ID';
COMMENT ON COLUMN purchase_order_items.purchase_order_id IS '采购订单ID';
COMMENT ON COLUMN purchase_order_items.sequence IS '序号';
COMMENT ON COLUMN purchase_order_items.material_id IS '物料ID';
COMMENT ON COLUMN purchase_order_items.bom_id IS 'BOM版本ID（可为空）';
COMMENT ON COLUMN purchase_order_items.material_desc IS '物料说明';
COMMENT ON COLUMN purchase_order_items.unit_id IS '采购单位ID';
COMMENT ON COLUMN purchase_order_items.qty IS '采购数量';
COMMENT ON COLUMN purchase_order_items.plan_confirm IS '计划确认（布尔值）';
COMMENT ON COLUMN purchase_order_items.sal_unit_id IS '销售单位ID（可为空）';
COMMENT ON COLUMN purchase_order_items.sal_qty IS '销售数量（可为空）';
COMMENT ON COLUMN purchase_order_items.sal_join_qty IS '销售订单关联数量（可为空）';
COMMENT ON COLUMN purchase_order_items.base_sal_join_qty IS '销售订单关联数量-基本（可为空）';
COMMENT ON COLUMN purchase_order_items.remarks IS '备注2';
COMMENT ON COLUMN purchase_order_items.sal_base_qty IS '销售基本数量（可为空）';
COMMENT ON COLUMN purchase_order_items.created_at IS '创建时间';
COMMENT ON COLUMN purchase_order_items.updated_at IS '更新时间';

COMMENT ON TABLE purchase_order_deliveries IS '采购订单交货明细表';
COMMENT ON COLUMN purchase_order_deliveries.id IS '采购订单交货明细ID';
COMMENT ON COLUMN purchase_order_deliveries.purchase_order_item_id IS '采购订单明细ID';
COMMENT ON COLUMN purchase_order_deliveries.sequence IS '交货明细序号';
COMMENT ON COLUMN purchase_order_deliveries.delivery_date IS '交货日期';
COMMENT ON COLUMN purchase_order_deliveries.plan_qty IS '计划数量';
COMMENT ON COLUMN purchase_order_deliveries.supplier_delivery_date IS '供应商发货日期（可为空）';
COMMENT ON COLUMN purchase_order_deliveries.pre_arrival_date IS '预计到货日期（可为空）';
COMMENT ON COLUMN purchase_order_deliveries.transport_lead_time IS '运输提前期（天数，可为空）';
COMMENT ON COLUMN purchase_order_deliveries.created_at IS '创建时间';
COMMENT ON COLUMN purchase_order_deliveries.updated_at IS '更新时间';

-- ============================================
-- 第七步：创建更新updated_at的触发器函数
-- ============================================
CREATE OR REPLACE FUNCTION update_purchase_order_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 为采购订单主表创建触发器
DROP TRIGGER IF EXISTS trigger_purchase_orders_updated_at ON purchase_orders;
CREATE TRIGGER trigger_purchase_orders_updated_at
    BEFORE UPDATE ON purchase_orders
    FOR EACH ROW
    EXECUTE FUNCTION update_purchase_order_updated_at();

-- 为采购订单明细表创建触发器
DROP TRIGGER IF EXISTS trigger_purchase_order_items_updated_at ON purchase_order_items;
CREATE TRIGGER trigger_purchase_order_items_updated_at
    BEFORE UPDATE ON purchase_order_items
    FOR EACH ROW
    EXECUTE FUNCTION update_purchase_order_updated_at();

-- 为采购订单交货明细表创建触发器
DROP TRIGGER IF EXISTS trigger_purchase_order_deliveries_updated_at ON purchase_order_deliveries;
CREATE TRIGGER trigger_purchase_order_deliveries_updated_at
    BEFORE UPDATE ON purchase_order_deliveries
    FOR EACH ROW
    EXECUTE FUNCTION update_purchase_order_updated_at();

-- ============================================
-- 第八步：添加采购订单权限
-- ============================================
-- 插入权限（如果不存在）
INSERT INTO permissions (name, description)
SELECT 'purchase_order:read', '查看采购订单'
WHERE NOT EXISTS (
    SELECT 1 FROM permissions WHERE name = 'purchase_order:read'
);

INSERT INTO permissions (name, description)
SELECT 'purchase_order:import', '导入采购订单'
WHERE NOT EXISTS (
    SELECT 1 FROM permissions WHERE name = 'purchase_order:import'
);

INSERT INTO permissions (name, description)
SELECT 'purchase_order:update', '更新采购订单'
WHERE NOT EXISTS (
    SELECT 1 FROM permissions WHERE name = 'purchase_order:update'
);

-- ============================================
-- 第九步：为角色分配采购订单权限
-- ============================================
-- 为ADMIN角色分配权限
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'ADMIN' 
    AND p.name IN ('purchase_order:read', 'purchase_order:import', 'purchase_order:update')
    AND NOT EXISTS (
        SELECT 1 FROM role_permissions rp
        WHERE rp.role_id = r.id AND rp.permission_id = p.id
    );

-- 为MANAGER角色分配权限
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'MANAGER' 
    AND p.name IN ('purchase_order:read', 'purchase_order:import', 'purchase_order:update')
    AND NOT EXISTS (
        SELECT 1 FROM role_permissions rp
        WHERE rp.role_id = r.id AND rp.permission_id = p.id
    );

-- ============================================
-- 第十步：验证数据
-- ============================================
DO $$
DECLARE
    orders_table_exists BOOLEAN;
    items_table_exists BOOLEAN;
    deliveries_table_exists BOOLEAN;
    status_type_exists BOOLEAN;
    permission_read_exists BOOLEAN;
    permission_import_exists BOOLEAN;
    permission_update_exists BOOLEAN;
BEGIN
    -- 检查表是否存在
    SELECT EXISTS (
        SELECT 1 FROM information_schema.tables 
        WHERE table_schema = 'public' 
        AND table_name = 'purchase_orders'
    ) INTO orders_table_exists;
    
    SELECT EXISTS (
        SELECT 1 FROM information_schema.tables 
        WHERE table_schema = 'public' 
        AND table_name = 'purchase_order_items'
    ) INTO items_table_exists;
    
    SELECT EXISTS (
        SELECT 1 FROM information_schema.tables 
        WHERE table_schema = 'public' 
        AND table_name = 'purchase_order_deliveries'
    ) INTO deliveries_table_exists;
    
    -- 检查枚举类型是否存在
    SELECT EXISTS (
        SELECT 1 FROM pg_type WHERE typname = 'purchase_order_status'
    ) INTO status_type_exists;
    
    -- 检查权限是否存在
    SELECT EXISTS (
        SELECT 1 FROM permissions WHERE name = 'purchase_order:read'
    ) INTO permission_read_exists;
    
    SELECT EXISTS (
        SELECT 1 FROM permissions WHERE name = 'purchase_order:import'
    ) INTO permission_import_exists;
    
    SELECT EXISTS (
        SELECT 1 FROM permissions WHERE name = 'purchase_order:update'
    ) INTO permission_update_exists;
    
    -- 输出验证结果
    RAISE NOTICE '========================================';
    RAISE NOTICE '迁移脚本执行结果：';
    RAISE NOTICE '  采购订单主表存在: %', orders_table_exists;
    RAISE NOTICE '  采购订单明细表存在: %', items_table_exists;
    RAISE NOTICE '  采购订单交货明细表存在: %', deliveries_table_exists;
    RAISE NOTICE '  订单状态枚举类型存在: %', status_type_exists;
    RAISE NOTICE '  查看权限存在: %', permission_read_exists;
    RAISE NOTICE '  导入权限存在: %', permission_import_exists;
    RAISE NOTICE '  更新权限存在: %', permission_update_exists;
    RAISE NOTICE '========================================';
END $$;

-- 提交事务
COMMIT;

-- ============================================
-- 迁移完成提示
-- ============================================
\echo ''
\echo '========================================'
\echo '采购订单表迁移脚本执行完成！'
\echo '========================================'
\echo ''
\echo '已创建的内容：'
\echo '  - purchase_orders 表'
\echo '  - purchase_order_items 表'
\echo '  - purchase_order_deliveries 表'
\echo '  - purchase_order_status 枚举类型'
\echo '  - 相关索引'
\echo '  - 更新触发器'
\echo '  - purchase_order:read 权限'
\echo '  - purchase_order:import 权限'
\echo '  - purchase_order:update 权限'
\echo '  - ADMIN 角色的采购订单权限'
\echo '  - MANAGER 角色的采购订单权限'
\echo ''
\echo '如果表已存在，脚本会安全跳过，不会报错。'
\echo ''

