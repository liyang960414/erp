-- ============================================
-- 创建物料组表
-- 版本: V4
-- ============================================

CREATE TABLE material_groups (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(200),
    parent_id BIGINT REFERENCES material_groups(id) ON DELETE SET NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 创建索引
CREATE INDEX idx_material_groups_code ON material_groups(code);
CREATE INDEX idx_material_groups_parent_id ON material_groups(parent_id);

-- 添加注释
COMMENT ON TABLE material_groups IS '物料组表';
COMMENT ON COLUMN material_groups.id IS '物料组ID';
COMMENT ON COLUMN material_groups.code IS '物料组编码（唯一）';
COMMENT ON COLUMN material_groups.name IS '物料组名称';
COMMENT ON COLUMN material_groups.description IS '物料组描述';
COMMENT ON COLUMN material_groups.parent_id IS '父级物料组ID（树形结构支持）';
COMMENT ON COLUMN material_groups.created_at IS '创建时间';
COMMENT ON COLUMN material_groups.updated_at IS '更新时间';

