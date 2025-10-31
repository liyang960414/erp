# 用户认证和权限管理系统

## 功能概述

本系统实现了完整的用户认证和基于角色的权限控制（RBAC）功能，包括：
- JWT身份验证
- 用户注册和登录
- 基于角色的权限管理
- Spring Security安全配置
- RESTful API设计

## 系统架构

### 技术栈
- **Spring Boot 4.0.0-RC1**
- **Spring Security** - 安全框架
- **Spring Data JPA** - 数据访问层
- **PostgreSQL** - 数据库
- **JWT** - 身份令牌
- **Java 25** - 编程语言

### 项目结构

```
com.sambound.erp/
├── config/              # 配置类
│   ├── DataInitializer.java      # 数据初始化
│   ├── JwtAuthenticationFilter.java  # JWT过滤器
│   ├── JwtUtil.java              # JWT工具类
│   ├── SecurityConfig.java       # 安全配置
│   └── UserDetailsServiceImpl.java   # 用户详情服务
├── controller/          # 控制器层
│   ├── AuthController.java      # 认证控制器
│   └── UserController.java      # 用户控制器
├── dto/                 # 数据传输对象
│   ├── ApiResponse.java         # API响应封装
│   ├── ErrorResponse.java       # 错误响应
│   ├── LoginRequest.java        # 登录请求
│   ├── LoginResponse.java       # 登录响应
│   └── RegisterRequest.java     # 注册请求
├── entity/              # 实体类
│   ├── Permission.java          # 权限实体
│   ├── Role.java                # 角色实体
│   └── User.java                # 用户实体
├── exception/           # 异常处理
│   ├── BusinessException.java   # 业务异常
│   └── GlobalExceptionHandler.java  # 全局异常处理
├── repository/          # 数据访问层
│   ├── PermissionRepository.java
│   ├── RoleRepository.java
│   └── UserRepository.java
└── service/             # 业务逻辑层
    ├── AuthService.java         # 认证服务
    └── UserService.java         # 用户服务
```

## 数据库设计

### 用户表 (users)
- id: 主键
- username: 用户名（唯一）
- password: 加密密码
- email: 邮箱（唯一）
- full_name: 全名
- enabled: 是否启用
- account_non_expired: 账户是否未过期
- account_non_locked: 账户是否未锁定
- credentials_non_expired: 凭证是否未过期
- created_at: 创建时间
- updated_at: 更新时间

### 角色表 (roles)
- id: 主键
- name: 角色名称（唯一）
- description: 描述

### 权限表 (permissions)
- id: 主键
- name: 权限名称（唯一）
- description: 描述

### 关联表
- user_roles: 用户-角色关联表
- role_permissions: 角色-权限关联表

## 预置数据

系统启动时自动创建以下数据：

### 默认角色
- **ADMIN**: 管理员角色，拥有所有权限
- **USER**: 普通用户角色，拥有基本的读权限

### 默认权限
- user:read, user:write, user:delete
- product:read, product:write, product:delete
- order:read, order:write, order:delete

### 默认管理员账户
- 用户名: `admin`
- 密码: `admin123`
- 角色: ADMIN

## API接口

### 认证接口

#### 1. 用户注册
```http
POST /api/auth/register
Content-Type: application/json

{
  "username": "testuser",
  "password": "password123",
  "email": "test@example.com",
  "fullName": "测试用户"
}
```

#### 2. 用户登录
```http
POST /api/auth/login
Content-Type: application/json

{
  "username": "admin",
  "password": "admin123"
}
```

响应示例：
```json
{
  "success": true,
  "message": "登录成功",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "tokenType": "Bearer",
    "userId": 1,
    "username": "admin",
    "email": "admin@example.com",
    "fullName": "系统管理员",
    "roles": ["ADMIN"]
  },
  "timestamp": "2024-01-01T12:00:00"
}
```

### 用户接口

#### 3. 获取当前用户信息
```http
GET /api/users/me
Authorization: Bearer {token}
```

#### 4. 根据ID获取用户（需要ADMIN权限）
```http
GET /api/users/{id}
Authorization: Bearer {token}
```

## 安全配置

### JWT配置
- 密钥: `mySecretKeyForJWTTokenGenerationAndValidationMustBeAtLeast256Bits`
- 过期时间: 24小时（86400000毫秒）

### CORS配置
允许的源:
- http://localhost:5173
- http://localhost:3000

### 公开接口
以下接口无需认证：
- `/api/auth/**` - 认证相关接口
- `/api/public/**` - 公共接口

其他接口需要有效的JWT令牌。

## 使用示例

### 使用curl测试

1. 注册新用户：
```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "newuser",
    "password": "password123",
    "email": "newuser@example.com",
    "fullName": "新用户"
  }'
```

2. 登录：
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "admin",
    "password": "admin123"
  }'
```

3. 获取当前用户信息：
```bash
curl -X GET http://localhost:8080/api/users/me \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

## 错误处理

系统使用标准的RFC 7807 Problem Details格式返回错误信息：

```json
{
  "type": "ValidationError",
  "title": "请求参数验证失败",
  "status": 400,
  "detail": "请检查输入数据",
  "timestamp": "2024-01-01T12:00:00",
  "errors": [
    {
      "field": "username",
      "message": "用户名不能为空"
    }
  ]
}
```

## 权限控制

使用Spring Security的`@PreAuthorize`注解进行方法级权限控制：

```java
@PreAuthorize("hasRole('ADMIN')")
public ResponseEntity<User> getUserById(@PathVariable Long id) {
    // 只有ADMIN角色可以访问
}
```

支持的角色检查：
- `hasRole('ROLE_NAME')` - 检查是否有指定角色
- `hasAuthority('PERMISSION_NAME')` - 检查是否有指定权限

## 注意事项

1. **生产环境配置**：
   - 修改JWT密钥为安全的随机字符串
   - 使用环境变量存储敏感配置
   - 配置HTTPS
   - 调整CORS配置

2. **密码安全**：
   - 使用BCrypt加密存储
   - 密码最小长度为6个字符

3. **数据库配置**：
   - 确保PostgreSQL服务运行
   - 修改`application.yaml`中的数据库连接信息
   - 创建数据库: `CREATE DATABASE erp_db;`

## 启动应用

1. 确保PostgreSQL运行并创建数据库
2. 运行Spring Boot应用
3. 系统会自动创建表结构和初始化数据
4. 使用默认管理员账户登录测试

## 开发建议

1. 为新增功能模块添加相应的权限
2. 遵循RESTful API设计规范
3. 使用DTO进行数据传输
4. 添加单元测试和集成测试
5. 使用日志记录重要操作

