-- ============================================
-- 创建物料表
-- 版本: V5
-- ============================================

CREATE TABLE materials (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    specification VARCHAR(500),
    mnemonic_code VARCHAR(50),
    old_number VARCHAR(50),
    description VARCHAR(1000),
    material_group_id BIGINT NOT NULL REFERENCES material_groups(id) ON DELETE RESTRICT,
    base_unit_id BIGINT NOT NULL REFERENCES units(id) ON DELETE RESTRICT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 创建索引
CREATE INDEX idx_materials_code ON materials(code);
CREATE INDEX idx_materials_material_group_id ON materials(material_group_id);
CREATE INDEX idx_materials_base_unit_id ON materials(base_unit_id);
CREATE INDEX idx_materials_name ON materials(name);

-- 添加注释
COMMENT ON TABLE materials IS '物料表';
COMMENT ON COLUMN materials.id IS '物料ID';
COMMENT ON COLUMN materials.code IS '物料编码（唯一）';
COMMENT ON COLUMN materials.name IS '物料名称';
COMMENT ON COLUMN materials.specification IS '规格';
COMMENT ON COLUMN materials.mnemonic_code IS '助记码';
COMMENT ON COLUMN materials.old_number IS '旧编号';
COMMENT ON COLUMN materials.description IS '描述';
COMMENT ON COLUMN materials.material_group_id IS '所属物料组ID';
COMMENT ON COLUMN materials.base_unit_id IS '基础单位ID';
COMMENT ON COLUMN materials.created_at IS '创建时间';
COMMENT ON COLUMN materials.updated_at IS '更新时间';

