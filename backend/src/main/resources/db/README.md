# 数据库脚本使用说明

本目录包含ERP系统的数据库初始化和维护脚本。

## 文件说明

### 初始化脚本

#### 1. `init_all.sql` - 完整初始化脚本（推荐）⭐
- **用途**: 一次性完成所有表结构和数据的初始化
- **内容**: 完整的表结构、索引、初始数据、注释
- **优点**: 单一文件，支持事务回滚，适合快速初始化
- **包含**: 用户、角色、权限、审计日志、单位、物料等所有表

### 维护脚本

#### 2. `drop_all_tables.sql` - 删除所有表
- **用途**: 完全删除数据库表和数据
- **警告**: ⚠️ 会删除所有数据，请谨慎使用！

#### 3. `recreate_database.sh` - 重建数据库（Linux/Mac）
- **用途**: 完全重建数据库的自动化脚本
- **功能**: 删除→创建→导入结构→导入数据
- **环境**: Linux/Mac/Unix

#### 4. `recreate_database.bat` - 重建数据库（Windows）
- **用途**: Windows版本的数据库重建脚本
- **功能**: 同上
- **环境**: Windows

#### 5. `QUERY_EXAMPLES.sql` - 查询示例脚本
- **用途**: 提供常用SQL查询示例
- **内容**: 统计、分析、维护等查询示例

## 使用方式

### 方式一：使用完整初始化脚本（最简单）

#### 使用init_all.sql一次性完成所有初始化
```bash
# Linux/Mac
psql -h localhost -p 5432 -U postgres -d erp_db -f init_all.sql

# Windows
psql -h localhost -p 5432 -U postgres -d erp_db -f init_all.sql
```

### 方式二：手动执行SQL脚本

#### 1. 连接到PostgreSQL
```bash
# Linux/Mac
psql -h localhost -p 5432 -U postgres -d erp_db

# Windows（如果有密码提示，输入密码）
psql -h localhost -p 5432 -U postgres -d erp_db
```

#### 2. 执行完整初始化脚本
```sql
\i src/main/resources/db/init_all.sql
```

### 方式三：使用自动化脚本（推荐）

#### Linux/Mac用户
```bash
# 1. 设置PostgreSQL密码环境变量（如果必要）
export PGPASSWORD=your_password

# 2. 进入脚本目录
cd backend/src/main/resources/db

# 3. 赋予执行权限
chmod +x recreate_database.sh

# 4. 执行脚本
./recreate_database.sh
```

#### Windows用户
```batch
REM 1. 进入脚本目录
cd backend\src\main\resources\db

REM 2. 执行脚本
recreate_database.bat
```

### 方式四：使用psql命令行

```bash
# 连接并执行完整初始化脚本
psql -h localhost -p 5432 -U postgres -d erp_db -f init_all.sql
```

### 方式五：使用Spring Boot自动初始化（开发环境）

如果`application.yaml`配置为：
```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: create  # 开发环境用create，生产环境用validate或none
```

Spring Boot会自动创建表。但不会插入初始数据，需要配合`DataInitializer.java`使用。

## 数据库结构

### 表关系图

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

audit_logs (审计日志表 - 独立表)

unit_groups (单位组)
  ↓ (一对多)
units (单位)
  ↓ (一对多)
unit_conversions (单位转换)

material_groups (物料组 - 树形结构)
  ↓ (一对多)
materials (物料)
```

### 表说明

| 表名 | 说明 | 记录数 |
|------|------|--------|
| users | 用户表 | 初始2个用户（admin, testuser） |
| roles | 角色表 | 初始3个角色（ADMIN, USER, MANAGER） |
| permissions | 权限表 | 初始12个权限 |
| user_roles | 用户角色关联 | 初始2条 |
| role_permissions | 角色权限关联 | 初始多条 |
| audit_logs | 审计日志表 | 初始为空 |
| unit_groups | 单位组表 | 初始为空 |
| units | 单位表 | 初始为空 |
| unit_conversions | 单位转换表 | 初始为空 |
| material_groups | 物料组表（支持树形结构） | 初始为空 |
| materials | 物料表 | 初始为空 |

### 重要字段说明

#### materials 表（物料表）
- **name**: TEXT 类型，支持任意长度
- **specification**: TEXT 类型，支持任意长度
- **mnemonic_code**: TEXT 类型，支持任意长度
- **description**: TEXT 类型，支持任意长度
- **code**: VARCHAR(50) 类型，物料编码（唯一）
- **old_number**: VARCHAR(50) 类型，旧编号

#### material_groups 表（物料组表）
- **name**: TEXT 类型，支持任意长度
- **description**: VARCHAR(200) 类型，描述
- **code**: VARCHAR(50) 类型，物料组编码（唯一）
- **parent_id**: BIGINT 类型，父级物料组ID（支持树形结构）

**注意**：物料表和物料组表的文本字段使用 TEXT 类型而非 VARCHAR，以支持长文本数据导入。

## 默认数据

### 用户账户

#### 管理员账户
- **用户名**: admin
- **密码**: admin123
- **邮箱**: admin@erp.example.com
- **角色**: ADMIN（拥有所有权限）

#### 测试账户
- **用户名**: testuser
- **密码**: admin123
- **邮箱**: testuser@erp.example.com
- **角色**: USER（只有查看权限）

### 角色说明

#### ADMIN（管理员）
拥有所有权限：
- user:read, user:write, user:delete
- product:read, product:write, product:delete
- order:read, order:write, order:delete
- system:read, system:write, system:delete

#### USER（普通用户）
只有查看权限：
- user:read
- product:read
- order:read

#### MANAGER（经理）
拥有管理权限：
- user:read
- product:read, product:write, product:delete
- order:read, order:write, order:delete
- system:read

### 权限说明

| 权限名称 | 说明 |
|---------|------|
| user:read | 查看用户 |
| user:write | 创建/编辑用户 |
| user:delete | 删除用户 |
| product:read | 查看产品 |
| product:write | 创建/编辑产品 |
| product:delete | 删除产品 |
| order:read | 查看订单 |
| order:write | 创建/编辑订单 |
| order:delete | 删除订单 |
| system:read | 查看系统设置 |
| system:write | 修改系统设置 |
| system:delete | 删除系统设置 |

## 常见问题

### 1. 密码忘记了？
使用脚本重建数据库会恢复默认密码：admin123

### 2. 表已存在怎么办？
脚本使用`DROP TABLE IF EXISTS`和`ON CONFLICT`，可以安全重复执行。

### 3. 如何修改默认数据？
编辑`init_all.sql`文件中的初始数据部分，修改后重新执行。

### 4. 生产环境如何使用？
**不要在生产环境使用这些脚本！**

生产环境应该：
1. 手动创建表结构
2. 使用数据库迁移工具（Flyway、Liquibase等）
3. 通过管理界面创建管理员账户
4. 定期备份数据库

### 5. 如何备份数据库？
```bash
# 备份
pg_dump -h localhost -p 5432 -U postgres erp_db > backup.sql

# 恢复
psql -h localhost -p 5432 -U postgres erp_db < backup.sql
```

### 6. 脚本执行失败？
检查：
- PostgreSQL服务是否运行
- 数据库用户权限是否正确
- 文件路径是否正确
- 查看错误日志

## 安全建议

1. **生产环境**：
   - 修改所有默认密码
   - 使用强密码策略
   - 定期审计用户权限
   - 启用数据库审计日志

2. **开发环境**：
   - 不要将敏感数据提交到版本控制
   - 使用环境变量管理数据库密码
   - 定期清理测试数据

3. **数据库安全**：
   - 限制数据库访问IP
   - 使用SSL加密连接
   - 定期更新PostgreSQL版本
   - 备份重要数据

## 版本历史

### v3.1 (2024-12-XX)
- 添加物料管理相关表（materials, material_groups）
- 添加单位管理相关表（units, unit_groups, unit_conversions）
- 物料表和物料组表的文本字段使用 TEXT 类型支持长文本
- 更新实体类映射以匹配数据库结构
- 修复物料导入时的字段长度限制问题

### v3.0 (2024-12-20)
- 添加审计日志表（audit_logs）
- 创建init_all.sql完整初始化脚本
- 更新所有文档和脚本支持审计表

### v1.0 (2024-01-01)
- 初始版本
- 创建基础表和索引
- 添加默认用户和角色
- 提供自动化重建脚本

## 联系支持

如有问题，请查看：
- `backend/AUTH_README.md` - 认证系统文档
- `backend/API_TEST_EXAMPLES.md` - API测试示例
- `backend/QUICKSTART.md` - 快速开始指南

