# 用户和权限管理功能使用指南

## 概述

本系统实现了完整的用户和权限管理功能，包括用户管理、角色管理和权限管理，支持前后端完整集成。

## 功能特性

### 用户管理
- ✅ 用户列表查看（分页）
- ✅ 创建用户
- ✅ 编辑用户信息
- ✅ 启用/禁用用户
- ✅ 删除用户
- ✅ 修改用户密码
- ✅ 分配角色

### 角色管理
- ✅ 角色列表查看
- ✅ 创建角色
- ✅ 编辑角色
- ✅ 删除角色
- ✅ 分配权限

### 权限管理
- ✅ 权限列表查看
- ✅ 权限详情查看
- ✅ 按模块分类显示

## 后端API

### 用户管理API

#### 1. 获取用户列表
```http
GET /api/users?page=0&size=10&sortBy=id&sortDir=DESC
Authorization: Bearer {token}
```

#### 2. 获取用户详情
```http
GET /api/users/{id}
Authorization: Bearer {token}
```

#### 3. 创建用户
```http
POST /api/users
Authorization: Bearer {token}
Content-Type: application/json

{
  "username": "newuser",
  "password": "password123",
  "email": "newuser@example.com",
  "fullName": "新用户",
  "enabled": true,
  "roleNames": ["USER"]
}
```

#### 4. 更新用户
```http
PUT /api/users/{id}
Authorization: Bearer {token}
Content-Type: application/json

{
  "email": "updated@example.com",
  "fullName": "更新后的名称",
  "enabled": true,
  "roleNames": ["USER", "MANAGER"]
}
```

#### 5. 删除用户
```http
DELETE /api/users/{id}
Authorization: Bearer {token}
```

#### 6. 修改密码
```http
PUT /api/users/{id}/password
Authorization: Bearer {token}
Content-Type: application/json

{
  "newPassword": "newpassword123"
}
```

### 角色管理API

#### 1. 获取角色列表
```http
GET /api/roles?page=0&size=10&sortBy=id&sortDir=ASC
Authorization: Bearer {token}
```

#### 2. 获取所有角色（不分页）
```http
GET /api/roles/list
Authorization: Bearer {token}
```

#### 3. 获取角色详情
```http
GET /api/roles/{id}
Authorization: Bearer {token}
```

#### 4. 创建角色
```http
POST /api/roles
Authorization: Bearer {token}
Content-Type: application/json

{
  "name": "MANAGER",
  "description": "经理角色",
  "permissionNames": ["user:read", "product:write", "order:write"]
}
```

#### 5. 更新角色
```http
PUT /api/roles/{id}
Authorization: Bearer {token}
Content-Type: application/json

{
  "description": "更新后的描述",
  "permissionNames": ["user:read", "user:write"]
}
```

#### 6. 删除角色
```http
DELETE /api/roles/{id}
Authorization: Bearer {token}
```

### 权限管理API

#### 1. 获取权限列表
```http
GET /api/permissions?page=0&size=100
Authorization: Bearer {token}
```

#### 2. 获取所有权限（不分页）
```http
GET /api/permissions/list
Authorization: Bearer {token}
```

#### 3. 获取权限详情
```http
GET /api/permissions/{id}
Authorization: Bearer {token}
```

## 前端使用

### 访问管理页面

1. **登录系统**
   - 使用管理员账户登录（admin/admin123）

2. **访问用户管理**
   - 导航菜单：用户管理 > 用户列表
   - 路径：http://localhost:5173/users/list

3. **访问角色管理**
   - 导航菜单：系统设置 > 角色管理
   - 路径：http://localhost:5173/system/roles

4. **访问权限管理**
   - 导航菜单：系统设置 > 权限管理
   - 路径：http://localhost:5173/system/permissions

### 用户管理操作

#### 添加用户
1. 点击"添加用户"按钮
2. 填写用户信息：
   - 用户名（必填，3-50字符）
   - 密码（必填，至少6字符）
   - 邮箱（必填，有效邮箱格式）
   - 全名（可选）
   - 状态（启用/禁用）
   - 角色（多选）
3. 点击"确定"保存

#### 编辑用户
1. 在用户列表中点击"编辑"按钮
2. 修改用户信息（用户名不可修改）
3. 点击"确定"保存

#### 启用/禁用用户
1. 在用户列表中点击"启用"或"禁用"按钮
2. 状态立即更新

#### 删除用户
1. 在用户列表中点击"删除"按钮
2. 确认删除操作
3. 用户将被永久删除

### 角色管理操作

#### 添加角色
1. 点击"添加角色"按钮
2. 填写角色信息：
   - 角色名称（必填，2-50字符）
   - 描述（可选）
   - 权限（多选，按模块分组）
3. 点击"确定"保存

#### 编辑角色
1. 在角色列表中点击"编辑"按钮
2. 修改角色描述和权限分配
3. 点击"确定"保存

#### 删除角色
1. 在角色列表中点击"删除"按钮
2. 确认删除操作
3. 如果角色正在被使用，删除将失败

### 权限管理操作

- 权限列表为只读显示
- 支持按模块分类查看
- 显示权限的完整列表和描述

## 权限控制

### 访问控制
- 所有管理API需要管理员（ADMIN）角色
- 前端页面通过路由守卫控制访问
- 菜单项根据用户角色动态显示/隐藏

### 角色权限
- **ADMIN**: 拥有所有权限
- **USER**: 拥有基本查看权限
- **MANAGER**: 拥有管理权限

### 预置权限
- user:read, user:write, user:delete
- product:read, product:write, product:delete
- order:read, order:write, order:delete
- system:read, system:write, system:delete

## 数据结构

### 用户表结构
```sql
users (
  id BIGSERIAL PRIMARY KEY,
  username VARCHAR(50) UNIQUE NOT NULL,
  password VARCHAR(100) NOT NULL,
  email VARCHAR(100) UNIQUE,
  full_name VARCHAR(100),
  enabled BOOLEAN DEFAULT TRUE,
  account_non_expired BOOLEAN DEFAULT TRUE,
  account_non_locked BOOLEAN DEFAULT TRUE,
  credentials_non_expired BOOLEAN DEFAULT TRUE,
  created_at TIMESTAMP,
  updated_at TIMESTAMP
)
```

### 角色表结构
```sql
roles (
  id BIGSERIAL PRIMARY KEY,
  name VARCHAR(50) UNIQUE NOT NULL,
  description VARCHAR(200)
)
```

### 权限表结构
```sql
permissions (
  id BIGSERIAL PRIMARY KEY,
  name VARCHAR(100) UNIQUE NOT NULL,
  description VARCHAR(200)
)
```

### 关联表
```sql
user_roles (user_id, role_id)
role_permissions (role_id, permission_id)
```

## 开发指南

### 后端开发

#### 添加新的Service
```java
@Service
@Transactional
public class MyService {
    private final MyRepository repository;
    
    public MyService(MyRepository repository) {
        this.repository = repository;
    }
    
    // 业务方法
}
```

#### 添加新的Controller
```java
@RestController
@RequestMapping("/api/my")
public class MyController {
    private final MyService service;
    
    public MyController(MyService service) {
        this.service = service;
    }
    
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<MyData>> getAll() {
        return ResponseEntity.ok(ApiResponse.success(service.getAll()));
    }
}
```

### 前端开发

#### 添加新的API
```typescript
// src/api/my.ts
import request from '@/utils/request'

export const myApi = {
  getData: () => request.get('/my'),
  createData: (data: any) => request.post('/my', data),
}
```

#### 添加新的页面
```vue
<template>
  <div class="my-container">
    <el-card>
      <el-table :data="data">
        <!-- 表格内容 -->
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { myApi } from '@/api/my'
// 业务逻辑
</script>
```

## 测试

### 后端测试

使用Postman或curl测试API：

```bash
# 登录获取token
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'

# 获取用户列表
curl -X GET http://localhost:8080/api/users \
  -H "Authorization: Bearer {token}"
```

### 前端测试

1. 启动前端开发服务器
2. 登录系统
3. 访问各个管理页面
4. 执行CRUD操作
5. 验证权限控制

## 安全建议

1. **生产环境**
   - 修改所有默认密码
   - 使用强密码策略
   - 启用HTTPS
   - 限制API访问频率
   - 定期审计用户权限

2. **开发环境**
   - 不要提交敏感数据
   - 使用环境变量管理配置
   - 定期清理测试数据

3. **数据安全**
   - 所有密码使用BCrypt加密
   - 敏感操作需要确认
   - 记录操作日志

## 常见问题

### 1. 无法删除角色
**原因**: 角色正在被用户使用
**解决**: 先移除用户的该角色分配，再删除角色

### 2. 用户名或邮箱已存在
**原因**: 唯一性约束
**解决**: 使用不同的用户名或邮箱

### 3. 权限不足
**原因**: 当前用户不是管理员
**解决**: 使用admin账户登录

### 4. Token过期
**原因**: JWT Token已过期
**解决**: 重新登录获取新token

## 下一步开发

- [ ] 添加用户导入/导出功能
- [ ] 实现操作日志记录
- [ ] 添加批量操作
- [ ] 实现高级搜索和筛选
- [ ] 添加数据导出为Excel
- [ ] 实现用户活动监控

## 文档索引

- [快速开始](backend/QUICKSTART.md)
- [认证系统](backend/AUTH_README.md)
- [API测试](backend/API_TEST_EXAMPLES.md)
- [数据库脚本](backend/src/main/resources/db/README.md)
- [前端指南](frontend/README.md)

