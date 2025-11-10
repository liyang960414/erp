-- ============================================
-- 迁移脚本：新增销售出库表并扩展销售订单状态
-- 版本: 1.0
-- 创建日期: 2025-11-10
-- 说明:
--   1. 新增销售出库主表与明细表，关联销售订单明细
--   2. 为销售订单/明细新增状态与发货数量字段
-- ============================================

BEGIN;

-- ============================================
-- 一、创建销售订单/明细状态枚举类型
-- ============================================

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'sale_order_status') THEN
        CREATE TYPE sale_order_status AS ENUM ('OPEN', 'CLOSED');
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'sale_order_item_status') THEN
        CREATE TYPE sale_order_item_status AS ENUM ('OPEN', 'CLOSED');
    END IF;
END $$;

-- ============================================
-- 二、扩展销售订单表
-- ============================================

ALTER TABLE sale_orders
    ADD COLUMN IF NOT EXISTS status sale_order_status NOT NULL DEFAULT 'OPEN';

COMMENT ON COLUMN sale_orders.status IS '销售订单状态：OPEN-进行中，CLOSED-已关闭';

-- ============================================
-- 三、扩展销售订单明细表
-- ============================================

ALTER TABLE sale_order_items
    ADD COLUMN IF NOT EXISTS status sale_order_item_status NOT NULL DEFAULT 'OPEN',
    ADD COLUMN IF NOT EXISTS delivered_qty NUMERIC(18, 6) NOT NULL DEFAULT 0,
    ADD CONSTRAINT IF NOT EXISTS chk_sale_order_item_delivered_qty CHECK (delivered_qty >= 0),
    ADD CONSTRAINT IF NOT EXISTS uq_sale_order_items_sequence UNIQUE (sequence);

COMMENT ON COLUMN sale_order_items.status IS '销售订单明细状态：OPEN-进行中，CLOSED-已关闭';
COMMENT ON COLUMN sale_order_items.delivered_qty IS '销售订单明细累计出库数量';

-- ============================================
-- 四、创建销售出库表结构
-- ============================================

CREATE TABLE IF NOT EXISTS sale_outstocks (
    id BIGSERIAL PRIMARY KEY,
    bill_no VARCHAR(100) NOT NULL UNIQUE,
    outstock_date DATE NOT NULL,
    note TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS sale_outstock_items (
    id BIGSERIAL PRIMARY KEY,
    sale_outstock_id BIGINT NOT NULL REFERENCES sale_outstocks(id) ON DELETE CASCADE,
    sequence INTEGER NOT NULL,
    sale_order_sequence INTEGER NOT NULL,
    material_id BIGINT NOT NULL REFERENCES materials(id) ON DELETE RESTRICT,
    unit_id BIGINT NOT NULL REFERENCES units(id) ON DELETE RESTRICT,
    qty NUMERIC(18, 6) NOT NULL,
    entry_note TEXT,
    wo_number VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_sale_outstock_item_sequence CHECK (sequence > 0),
    CONSTRAINT chk_sale_outstock_item_qty CHECK (qty > 0)
);

-- ============================================
-- 五、创建索引
-- ============================================

CREATE INDEX IF NOT EXISTS idx_sale_outstocks_bill_no ON sale_outstocks(bill_no);
CREATE INDEX IF NOT EXISTS idx_sale_outstocks_outstock_date ON sale_outstocks(outstock_date);

CREATE INDEX IF NOT EXISTS idx_sale_outstock_items_outstock_id ON sale_outstock_items(sale_outstock_id);
CREATE INDEX IF NOT EXISTS idx_sale_outstock_items_order_sequence ON sale_outstock_items(sale_order_sequence);
CREATE INDEX IF NOT EXISTS idx_sale_outstock_items_material_id ON sale_outstock_items(material_id);

-- ============================================
-- 六、添加注释
-- ============================================

COMMENT ON TABLE sale_outstocks IS '销售出库主表';
COMMENT ON COLUMN sale_outstocks.id IS '销售出库ID';
COMMENT ON COLUMN sale_outstocks.bill_no IS '销售出库单据编号（唯一）';
COMMENT ON COLUMN sale_outstocks.outstock_date IS '出库日期';
COMMENT ON COLUMN sale_outstocks.note IS '出库备注';

COMMENT ON TABLE sale_outstock_items IS '销售出库明细表';
COMMENT ON COLUMN sale_outstock_items.id IS '销售出库明细ID';
COMMENT ON COLUMN sale_outstock_items.sale_outstock_id IS '销售出库ID';
COMMENT ON COLUMN sale_outstock_items.sequence IS '明细序号';
COMMENT ON COLUMN sale_outstock_items.sale_order_sequence IS '关联的销售订单明细序号';
COMMENT ON COLUMN sale_outstock_items.material_id IS '物料ID';
COMMENT ON COLUMN sale_outstock_items.unit_id IS '出库单位ID';
COMMENT ON COLUMN sale_outstock_items.qty IS '实发数量';
COMMENT ON COLUMN sale_outstock_items.entry_note IS '明细备注';
COMMENT ON COLUMN sale_outstock_items.wo_number IS '本司WO编号';

-- ============================================
-- 七、权限配置
-- ============================================

INSERT INTO permissions (name, description)
SELECT 'sale_outstock:read', '查看销售出库单'
WHERE NOT EXISTS (
    SELECT 1 FROM permissions WHERE name = 'sale_outstock:read'
);

INSERT INTO permissions (name, description)
SELECT 'sale_outstock:import', '导入销售出库单'
WHERE NOT EXISTS (
    SELECT 1 FROM permissions WHERE name = 'sale_outstock:import'
);

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.name = 'sale_outstock:import'
WHERE r.name = 'ADMIN'
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.name = 'sale_outstock:read'
WHERE r.name = 'ADMIN'
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.name = 'sale_outstock:read'
WHERE r.name = 'MANAGER'
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.name = 'sale_outstock:import'
WHERE r.name = 'MANAGER'
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

COMMIT;

