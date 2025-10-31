# 审计日志系统说明

## 概述

本系统实现了完整的审计日志功能，用于记录系统中的所有关键操作，满足审计和安全性要求。

## 功能特性

### 1. 数据库设计

创建了`audit_logs`表，包含以下字段：
- **基本信息**：ID、操作者用户名、用户ID
- **操作信息**：操作类型、模块、资源类型、资源ID、操作描述
- **请求信息**：请求方法、请求URI、IP地址
- **结果信息**：操作状态（SUCCESS/FAILURE）、错误消息
- **时间信息**：创建时间

### 2. 后端实现

#### 实体类
- `AuditLog.java` - 审计日志实体，使用JPA注解映射到数据库

#### Repository层
- `AuditLogRepository.java` - 提供多种查询方法：
  - 按用户名查询
  - 按操作类型查询
  - 按模块查询
  - 按状态查询
  - 按时间范围查询
  - 多条件组合查询

#### Service层
- `AuditLogService.java` - 审计日志服务
  - 异步保存审计日志（使用Java 25的Virtual Threads）
  - 同步保存审计日志（用于关键操作）
  - 各种查询方法

#### 工具类
- `AuditLogHelper.java` - 审计日志构建器
  - 提供便捷的日志构建方法
  - 定义标准操作类型常量
  - 定义标准模块类型常量

#### Controller层
- `AuditLogController.java` - 审计日志查询接口
  - 分页查询审计日志
  - 多条件组合查询
  - 按用户名、操作类型、模块查询

#### 异步配置
- `AsyncConfig.java` - 配置Virtual Thread执行器用于异步记录日志

### 3. 现有代码增强

在以下Service中添加了审计日志记录：

#### AuthService（认证服务）
- **登录操作**：记录成功/失败登录，包括用户名和结果
- **注册操作**：记录新用户注册

#### UserManagementService（用户管理服务）
- **创建用户**：记录用户创建操作
- **更新用户**：记录用户信息修改
- **删除用户**：记录用户删除操作
- **修改密码**：记录密码修改操作

#### RoleService（角色管理服务）
- **创建角色**：记录角色创建
- **更新角色**：记录角色权限修改
- **删除角色**：记录角色删除

所有关键操作都同时使用：
- **调试日志**（`logger.debug()`）：记录方法进入和重要参数
- **审计日志**（`logger.info()`）：记录关键操作成功
- **错误日志**（`logger.error()`）：记录操作失败和异常

### 4. 前端实现

#### API层
- `auditLog.ts` - 审计日志API接口封装
  - 获取审计日志列表
  - 按用户名查询
  - 按操作类型查询
  - 按模块查询

#### 视图层
- `AuditLogView.vue` - 审计日志管理页面
  - 搜索表单（用户名、操作类型、模块、状态、时间范围）
  - 数据表格展示
  - 操作状态标签
  - 错误详情查看
  - 分页功能

#### 路由配置
- 在`/system/audit-logs`路径添加审计日志页面
- 仅管理员（ADMIN）可访问

#### 国际化
- 添加中文审计日志相关翻译
- 支持所有界面文本的国际化

### 5. 数据库迁移

创建了迁移脚本：
- `V3__add_audit_logs.sql` - 创建audit_logs表和索引

## 使用方法

### 1. 数据库迁移

执行数据库迁移脚本：

```bash
cd backend/src/main/resources/db
psql -h localhost -U postgres -d erp_db -f migration/V3__add_audit_logs.sql
```

### 2. 后端启动

确保使用Java 25和Spring Boot 4.0.0-RC1

```bash
cd backend
mvn clean install
mvn spring-boot:run
```

### 3. 前端启动

```bash
cd frontend
npm install
npm run dev
```

### 4. 使用审计日志功能

1. 登录系统（使用管理员账户）
2. 导航到"系统设置" -> "审计日志"
3. 使用搜索表单查询审计日志
4. 点击"查看错误"查看失败操作的详细信息

## 审计日志记录的操作类型

### 认证模块（AUTH）
- `LOGIN` - 用户登录
- `REGISTER` - 用户注册

### 用户管理模块（USER_MANAGEMENT）
- `CREATE_USER` - 创建用户
- `UPDATE_USER` - 更新用户
- `DELETE_USER` - 删除用户
- `CHANGE_PASSWORD` - 修改密码

### 角色管理模块（ROLE_MANAGEMENT）
- `CREATE_ROLE` - 创建角色
- `UPDATE_ROLE` - 更新角色
- `DELETE_ROLE` - 删除角色

## 技术特点

1. **Java 25特性**：使用Virtual Threads实现异步日志记录，提高系统性能
2. **异步处理**：审计日志记录不影响主业务流程性能
3. **灵活查询**：支持多条件组合查询，满足各种审计需求
4. **完整记录**：记录操作的上下文信息（操作者、时间、结果等）
5. **安全控制**：仅管理员可访问审计日志

## 后续扩展建议

1. 添加更多操作类型的审计日志（如权限管理）
2. 实现审计日志的自动归档和清理
3. 添加审计日志的导出功能（Excel/CSV）
4. 实现日志统计分析（操作趋势、失败率等）
5. 添加实时监控和告警功能

## 代码规范

所有代码遵循项目规范：
- 使用构造函数注入
- 使用Java Record作为DTO
- 使用SLF4J记录日志
- 审计日志使用`logger.info()`
- 调试信息使用`logger.debug()`
- 错误信息使用`logger.error()`

