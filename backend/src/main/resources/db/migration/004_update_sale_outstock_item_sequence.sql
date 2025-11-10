-- ============================================
-- 迁移脚本：调整销售出库明细关联字段
-- 版本: 1.0
-- 创建日期: 2025-11-10
-- 说明:
--   - 将 sale_outstock_items 表中原先指向销售订单明细 ID 的列改为存储销售明细序号
--   - 移除外键约束及相关索引
-- ============================================

BEGIN;

-- 移除旧的外键约束（如果存在）
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'sale_outstock_items_sale_order_item_id_fkey'
          AND table_name = 'sale_outstock_items'
    ) THEN
        ALTER TABLE sale_outstock_items DROP CONSTRAINT sale_outstock_items_sale_order_item_id_fkey;
    END IF;
END $$;

-- 删除旧索引
DROP INDEX IF EXISTS idx_sale_outstock_items_order_item_id;

-- 重命名列并转换数据类型
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'sale_outstock_items'
          AND column_name = 'sale_order_item_id'
    ) THEN
        ALTER TABLE sale_outstock_items
            RENAME COLUMN sale_order_item_id TO sale_order_sequence;
    END IF;
END $$;

ALTER TABLE sale_outstock_items
    ALTER COLUMN sale_order_sequence TYPE INTEGER USING sale_order_sequence::INTEGER,
    ALTER COLUMN sale_order_sequence SET NOT NULL;

ALTER TABLE sale_order_items
    ADD CONSTRAINT IF NOT EXISTS uq_sale_order_items_sequence UNIQUE (sequence);

-- 创建新的索引
CREATE INDEX IF NOT EXISTS idx_sale_outstock_items_order_sequence
    ON sale_outstock_items(sale_order_sequence);

-- 更新列注释
COMMENT ON COLUMN sale_outstock_items.sale_order_sequence IS '关联的销售订单明细序号';

COMMIT;

