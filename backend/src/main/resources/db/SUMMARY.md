# 数据库脚本总览

本目录包含完整的数据库管理脚本，包括表结构、初始数据和自动化工具。

## 📋 文件清单

| 文件名 | 类型 | 说明 |
|--------|------|------|
| `migration/V1__init_schema.sql` | SQL | 创建表结构和索引 |
| `migration/V2__init_data.sql` | SQL | 插入初始数据 |
| `drop_all_tables.sql` | SQL | 删除所有表 |
| `recreate_database.sh` | Shell | Linux/Mac自动化重建脚本 |
| `recreate_database.bat` | Batch | Windows自动化重建脚本 |
| `README.md` | 文档 | 详细使用说明 |
| `SUMMARY.md` | 文档 | 本文件 |

## 🚀 快速开始

### 方式1: 自动化重建（推荐）

#### Windows用户
```batch
cd backend\src\main\resources\db
recreate_database.bat
```

#### Linux/Mac用户
```bash
cd backend/src/main/resources/db
chmod +x recreate_database.sh
./recreate_database.sh
```

### 方式2: 手动执行SQL

```bash
# 连接到数据库
psql -h localhost -p 5432 -U postgres -d erp_db

# 在psql中执行
\i migration/V1__init_schema.sql
\i migration/V2__init_data.sql
```

### 方式3: 使用psql命令行

```bash
# 执行建表脚本
psql -h localhost -U postgres -d erp_db -f migration/V1__init_schema.sql

# 执行数据脚本
psql -h localhost -U postgres -d erp_db -f migration/V2__init_data.sql
```

## 📊 数据库结构

### 表关系

```
users (用户)
  ↓ (多对多)
user_roles (用户角色关联)
  ↓
roles (角色)
  ↓ (多对多)
role_permissions (角色权限关联)
  ↓
permissions (权限)
```

### 表列表

| 表名 | 说明 | 主键 |
|------|------|------|
| users | 用户表 | id |
| roles | 角色表 | id |
| permissions | 权限表 | id |
| user_roles | 用户角色关联 | (user_id, role_id) |
| role_permissions | 角色权限关联 | (role_id, permission_id) |

## 🔐 默认账户

### 管理员账户
- **用户名**: admin
- **密码**: admin123
- **角色**: ADMIN
- **权限**: 全部

### 测试账户
- **用户名**: testuser
- **密码**: admin123
- **角色**: USER
- **权限**: 查看权限

## 🔧 常用操作

### 完全重建数据库
```bash
./recreate_database.sh  # Linux/Mac
recreate_database.bat   # Windows
```

### 只删除表
```bash
psql -h localhost -U postgres -d erp_db -f drop_all_tables.sql
```

### 只重建表结构
```bash
psql -h localhost -U postgres -d erp_db -f migration/V1__init_schema.sql
```

### 重新插入初始数据
```bash
psql -h localhost -U postgres -d erp_db -f migration/V2__init_data.sql
```

### 备份数据库
```bash
pg_dump -h localhost -U postgres erp_db > backup_$(date +%Y%m%d_%H%M%S).sql
```

### 恢复数据库
```bash
psql -h localhost -U postgres -d erp_db < backup_20240101_120000.sql
```

## 📝 脚本说明

### V1__init_schema.sql
- 创建所有表结构
- 创建索引
- 添加外键约束
- 添加表注释和列注释

**特性**:
- 使用 `BIGSERIAL` 自动生成ID
- 使用 `TIMESTAMP` 记录时间
- 支持级联删除
- 完整的索引优化

### V2__init_data.sql
- 插入12个权限
- 插入3个角色
- 插入2个用户
- 配置角色权限关系
- 配置用户角色关系

**特性**:
- 使用 `ON CONFLICT DO NOTHING` 避免重复插入
- 密码使用BCrypt加密
- 包含验证查询

### drop_all_tables.sql
- 删除所有表
- 使用CASCADE确保删除外键关联

**⚠️ 警告**: 此脚本会删除所有数据！

### recreate_database.sh/bat
- 自动检查数据库连接
- 交互式确认删除
- 执行完整重建流程
- 验证数据完整性
- 彩色输出提示

## 🛠️ 开发指南

### 添加新表

1. 在 `V1__init_schema.sql` 中添加CREATE TABLE语句
2. 添加索引和注释
3. 更新文档

### 添加初始数据

1. 在 `V2__init_data.sql` 中添加INSERT语句
2. 使用 `ON CONFLICT DO NOTHING`
3. 添加验证查询

### 修改表结构

1. **开发环境**: 使用JPA自动更新
```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: update
```

2. **生产环境**: 创建迁移脚本
```sql
-- V3__add_new_column.sql
ALTER TABLE users ADD COLUMN phone VARCHAR(20);
```

## 🔒 安全建议

1. **开发环境**:
   - 可以使用默认账户
   - 定期重建数据库
   - 不要提交敏感数据

2. **测试环境**:
   - 修改默认密码
   - 创建测试账户
   - 定期清理数据

3. **生产环境**:
   - 不要使用这些脚本！
   - 手动创建表结构
   - 使用数据库迁移工具
   - 修改所有默认密码
   - 启用数据库审计

## ❓ 故障排查

### 连接失败
```
错误: 无法连接到PostgreSQL
解决: 检查PostgreSQL服务是否运行
     检查连接参数是否正确
```

### 权限不足
```
错误: permission denied
解决: 确保使用postgres用户
     检查数据库用户权限
```

### 表已存在
```
错误: relation already exists
解决: 先执行drop_all_tables.sql
     或使用recreate_database脚本
```

### 外键约束
```
错误: foreign key constraint failed
解决: 按正确顺序删除表
     或使用CASCADE选项
```

## 📚 相关文档

- [详细使用说明](README.md)
- [认证系统文档](../../AUTH_README.md)
- [API测试示例](../../API_TEST_EXAMPLES.md)
- [快速开始指南](../../QUICKSTART.md)

## 🔄 版本历史

### v1.0 (2024-01-01)
- 初始版本
- 完整的表结构
- 基础权限系统
- 自动化脚本

## 📞 获取帮助

如有问题，请：
1. 查看详细文档 [README.md](README.md)
2. 检查错误日志
3. 参考PostgreSQL文档
4. 联系开发团队

