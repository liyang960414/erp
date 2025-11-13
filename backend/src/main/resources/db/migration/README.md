# 数据库迁移脚本

本目录包含数据库迁移脚本，用于在现有数据库上添加新功能或修改表结构。

## 脚本说明

### 001_add_suppliers_table.sql
- **用途**: 添加供应商表和相关权限
- **内容**: 
  - 创建 `suppliers` 表
  - 创建索引 `idx_suppliers_code`
  - 添加表注释和列注释
  - 添加 `supplier:import` 权限
  - 为 ADMIN 角色分配供应商导入权限
  - 为 MANAGER 角色分配供应商导入权限
- **安全性**: 使用 `IF NOT EXISTS` 和条件检查，可以安全地重复执行
- **适用场景**: 在生产数据库中添加供应商功能

### fix_admin_supplier_permission.sql
- **用途**: 快速修复脚本，为ADMIN角色添加supplier:import权限
- **适用场景**: 如果之前执行过旧版本的迁移脚本（没有为ADMIN分配权限），使用此脚本快速修复
- **使用方法**: 
  ```bash
  psql -h localhost -p 5432 -U postgres -d erp_db -f migration/fix_admin_supplier_permission.sql
  ```

### 005_add_import_tasks_tables.sql
- **用途**: 添加导入任务主表、任务子项、依赖关系和失败记录表
- **内容**:
  - 创建 `import_tasks`、`import_task_items`、`import_task_dependencies`、`import_task_failures` 表
  - 创建相关索引及唯一约束
  - 添加表注释和列注释
  - 提供回滚脚本 `005_add_import_tasks_tables_rollback.sql`
- **安全性**: 使用 `IF NOT EXISTS` 和索引存在检查，可以安全重复执行
- **适用场景**: 在现有数据库中启用后台导入任务功能

## 使用方法

### 方式一：使用 psql 命令行执行

```bash
# Linux/Mac
psql -h localhost -p 5432 -U postgres -d erp_db -f migration/001_add_suppliers_table.sql

# Windows
psql -h localhost -p 5432 -U postgres -d erp_db -f migration\001_add_suppliers_table.sql
```

### 方式二：在 psql 中执行

```bash
# 连接到数据库
psql -h localhost -p 5432 -U postgres -d erp_db

# 在 psql 中执行
\i migration/001_add_suppliers_table.sql
```

### 方式三：使用数据库管理工具

可以使用 pgAdmin、DBeaver 等工具打开并执行脚本。

## 注意事项

1. **备份数据**: 在执行迁移脚本前，建议先备份数据库
2. **测试环境**: 建议先在测试环境执行，验证无误后再在生产环境执行
3. **权限检查**: 确保执行用户有足够的权限创建表、索引和插入数据
4. **重复执行**: 脚本设计为可以安全地重复执行，不会因为对象已存在而报错

## 快速修复（如果ADMIN角色没有权限）

如果之前执行过旧版本的迁移脚本，ADMIN角色可能没有supplier:import权限。使用快速修复脚本：

```bash
psql -h localhost -p 5432 -U postgres -d erp_db -f migration/fix_admin_supplier_permission.sql
```

执行后需要重新登录系统以使权限生效。

## 验证迁移结果

执行迁移后，可以通过以下SQL验证：

```sql
-- 检查表是否存在
SELECT EXISTS (
    SELECT 1 FROM information_schema.tables 
    WHERE table_schema = 'public' 
    AND table_name = 'suppliers'
);

-- 检查权限是否存在
SELECT * FROM permissions WHERE name = 'supplier:import';

-- 检查索引是否存在
SELECT * FROM pg_indexes WHERE tablename = 'suppliers';

-- 检查ADMIN角色的权限
SELECT r.name AS role_name, p.name AS permission_name
FROM roles r
JOIN role_permissions rp ON r.id = rp.role_id
JOIN permissions p ON rp.permission_id = p.id
WHERE r.name = 'ADMIN' AND p.name = 'supplier:import';

-- 检查MANAGER角色的权限
SELECT r.name AS role_name, p.name AS permission_name
FROM roles r
JOIN role_permissions rp ON r.id = rp.role_id
JOIN permissions p ON rp.permission_id = p.id
WHERE r.name = 'MANAGER' AND p.name = 'supplier:import';
```

## 迁移脚本命名规范

- 格式: `NNN_description.sql`
- NNN: 三位数字序号，表示执行顺序
- description: 简短描述，使用下划线分隔
- 示例: `001_add_suppliers_table.sql`, `002_add_new_feature.sql`

## 回滚脚本

如果需要回滚，可以创建对应的回滚脚本，命名格式为 `NNN_description_rollback.sql`。

例如，回滚供应商表的脚本可以命名为 `001_add_suppliers_table_rollback.sql`。

