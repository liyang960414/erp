-- ============================================
-- 迁移脚本：为采购订单明细表添加委外订单明细关联字段
-- 版本: 1.0
-- 创建日期: 2024-12-XX
-- 说明: 在采购订单明细表中添加 sub_req_order_item_id 字段，用于关联委外订单明细
-- 适用: PostgreSQL 12+
-- ============================================

-- 开始事务
BEGIN;

-- ============================================
-- 第一步：添加 sub_req_order_item_id 字段
-- ============================================
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_schema = 'public' 
        AND table_name = 'purchase_order_items' 
        AND column_name = 'sub_req_order_item_id'
    ) THEN
        ALTER TABLE purchase_order_items 
        ADD COLUMN sub_req_order_item_id BIGINT;
    END IF;
END $$;

-- ============================================
-- 第二步：添加外键约束
-- ============================================
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints 
        WHERE constraint_schema = 'public' 
        AND constraint_name = 'fk_purchase_order_items_sub_req_order_item_id'
    ) THEN
        ALTER TABLE purchase_order_items 
        ADD CONSTRAINT fk_purchase_order_items_sub_req_order_item_id 
        FOREIGN KEY (sub_req_order_item_id) 
        REFERENCES sub_req_order_items(id) 
        ON DELETE SET NULL;
    END IF;
END $$;

-- ============================================
-- 第三步：创建索引
-- ============================================
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes 
        WHERE schemaname = 'public' 
        AND tablename = 'purchase_order_items' 
        AND indexname = 'idx_purchase_order_items_sub_req_order_item_id'
    ) THEN
        CREATE INDEX idx_purchase_order_items_sub_req_order_item_id 
        ON purchase_order_items(sub_req_order_item_id);
    END IF;
END $$;

-- ============================================
-- 第四步：添加列注释
-- ============================================
COMMENT ON COLUMN purchase_order_items.sub_req_order_item_id IS '关联的委外订单明细ID';

-- 提交事务
COMMIT;

