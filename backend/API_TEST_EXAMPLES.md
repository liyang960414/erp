# API 测试示例

本文档提供使用curl和Postman测试API的示例。

## 前置条件

1. PostgreSQL数据库运行中
2. 数据库已创建：`CREATE DATABASE erp_db;`
3. Spring Boot应用已启动（默认端口8080）
4. 应用会自动创建默认管理员账户

## 1. 用户注册

### 注册新用户
```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "password": "password123",
    "email": "testuser@example.com",
    "fullName": "测试用户"
  }'
```

### 成功响应
```json
{
  "success": true,
  "message": "注册成功",
  "data": {
    "id": 2,
    "username": "testuser",
    "email": "testuser@example.com",
    "fullName": "测试用户",
    "enabled": true,
    "accountNonExpired": true,
    "accountNonLocked": true,
    "credentialsNonExpired": true,
    "roles": [
      {
        "id": 2,
        "name": "USER",
        "description": "普通用户角色"
      }
    ]
  },
  "timestamp": "2024-01-01T12:00:00"
}
```

### 错误响应示例（用户名已存在）
```json
{
  "type": "BusinessError",
  "title": "业务错误",
  "status": 400,
  "detail": "用户名已存在",
  "timestamp": "2024-01-01T12:00:00",
  "errors": null
}
```

### 错误响应示例（验证失败）
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
      "message": "用户名长度必须在3-50个字符之间"
    },
    {
      "field": "password",
      "message": "密码长度至少为6个字符"
    }
  ]
}
```

## 2. 用户登录

### 使用默认管理员登录
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "admin",
    "password": "admin123"
  }'
```

### 成功响应
```json
{
  "success": true,
  "message": "登录成功",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VySWQiOjEsInN1YiI6ImFkbWluIiwiaWF0IjoxNzA0MTI5NjAwLCJleHAiOjE3MDQyMTYwMDB9...",
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

### 错误响应示例（密码错误）
```json
{
  "type": "AuthenticationError",
  "title": "认证失败",
  "status": 401,
  "detail": "用户名或密码错误",
  "timestamp": "2024-01-01T12:00:00",
  "errors": null
}
```

### 保存Token
将返回的token保存为环境变量，后续请求使用：
```bash
# Linux/Mac
export JWT_TOKEN="your_jwt_token_here"

# Windows PowerShell
$env:JWT_TOKEN="your_jwt_token_here"
```

## 3. 获取当前用户信息

### 需要认证的请求
```bash
curl -X GET http://localhost:8080/api/users/me \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

### 成功响应
```json
{
  "success": true,
  "message": "操作成功",
  "data": {
    "id": 1,
    "username": "admin",
    "email": "admin@example.com",
    "fullName": "系统管理员",
    "enabled": true,
    "accountNonExpired": true,
    "accountNonLocked": true,
    "credentialsNonExpired": true,
    "roles": [
      {
        "id": 1,
        "name": "ADMIN",
        "description": "管理员角色",
        "permissions": [
          {
            "id": 1,
            "name": "user:read",
            "description": "权限: user:read"
          },
          {
            "id": 2,
            "name": "user:write",
            "description": "权限: user:write"
          }
          // ... 更多权限
        ]
      }
    ]
  },
  "timestamp": "2024-01-01T12:00:00"
}
```

### 错误响应示例（未认证）
```json
{
  "type": "InternalError",
  "title": "服务器内部错误",
  "status": 500,
  "detail": "系统繁忙，请稍后重试",
  "timestamp": "2024-01-01T12:00:00",
  "errors": null
}
```

## 4. 根据ID获取用户（需要ADMIN权限）

```bash
curl -X GET http://localhost:8080/api/users/1 \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

## Postman 测试示例

### 1. 创建环境变量
在Postman中创建环境变量：
- `base_url`: `http://localhost:8080`
- `jwt_token`: （登录后保存）

### 2. 注册请求
- Method: `POST`
- URL: `{{base_url}}/api/auth/register`
- Headers:
  - `Content-Type: application/json`
- Body (raw JSON):
```json
{
  "username": "testuser",
  "password": "password123",
  "email": "testuser@example.com",
  "fullName": "测试用户"
}
```

### 3. 登录请求
- Method: `POST`
- URL: `{{base_url}}/api/auth/login`
- Headers:
  - `Content-Type: application/json`
- Body (raw JSON):
```json
{
  "username": "admin",
  "password": "admin123"
}
```

### 4. 自动保存Token
在登录请求的Tests标签中添加：
```javascript
if (pm.response.code === 200) {
    var jsonData = pm.response.json();
    pm.environment.set("jwt_token", jsonData.data.token);
}
```

### 5. 获取用户信息
- Method: `GET`
- URL: `{{base_url}}/api/users/me`
- Headers:
  - `Authorization: Bearer {{jwt_token}}`

## JavaScript/Fetch 示例

```javascript
// 登录
async function login(username, password) {
  const response = await fetch('http://localhost:8080/api/auth/login', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ username, password })
  });
  
  const data = await response.json();
  
  if (data.success) {
    // 保存token
    localStorage.setItem('jwt_token', data.data.token);
    console.log('登录成功:', data.data);
  } else {
    console.error('登录失败:', data);
  }
  
  return data;
}

// 获取当前用户信息
async function getCurrentUser() {
  const token = localStorage.getItem('jwt_token');
  
  const response = await fetch('http://localhost:8080/api/users/me', {
    headers: {
      'Authorization': `Bearer ${token}`
    }
  });
  
  const data = await response.json();
  console.log('当前用户:', data.data);
  return data;
}

// 使用示例
login('admin', 'admin123').then(() => {
  getCurrentUser();
});
```

## Python 示例

```python
import requests
import json

BASE_URL = "http://localhost:8080"

# 登录
def login(username, password):
    url = f"{BASE_URL}/api/auth/login"
    payload = {
        "username": username,
        "password": password
    }
    
    response = requests.post(url, json=payload)
    data = response.json()
    
    if data.get("success"):
        token = data["data"]["token"]
        print(f"登录成功，Token: {token[:50]}...")
        return token
    else:
        print(f"登录失败: {data}")
        return None

# 获取当前用户
def get_current_user(token):
    url = f"{BASE_URL}/api/users/me"
    headers = {
        "Authorization": f"Bearer {token}"
    }
    
    response = requests.get(url, headers=headers)
    data = response.json()
    
    if data.get("success"):
        print(f"当前用户: {data['data']['username']}")
        print(f"角色: {data['data']['roles']}")
    else:
        print(f"获取失败: {data}")

# 使用示例
if __name__ == "__main__":
    # 登录
    token = login("admin", "admin123")
    
    if token:
        # 获取用户信息
        get_current_user(token)
```

## 常见问题

### 1. 401 Unauthorized
- 检查token是否正确
- 检查Authorization header格式是否正确
- 检查token是否过期（默认24小时）

### 2. 403 Forbidden
- 检查用户是否有足够的权限
- 需要ADMIN权限的接口需要登录管理员账户

### 3. 500 Internal Server Error
- 检查数据库连接
- 检查应用日志
- 确保数据库表已创建

### 4. 连接拒绝
- 确保Spring Boot应用正在运行
- 检查端口是否正确（默认8080）
- 检查防火墙设置

## 数据库查询示例

连接到PostgreSQL查看数据：

```sql
-- 查看所有用户
SELECT * FROM users;

-- 查看所有角色
SELECT * FROM roles;

-- 查看所有权限
SELECT * FROM permissions;

-- 查看用户的角色
SELECT u.username, r.name as role_name
FROM users u
JOIN user_roles ur ON u.id = ur.user_id
JOIN roles r ON ur.role_id = r.id;

-- 查看角色的权限
SELECT r.name as role_name, p.name as permission_name
FROM roles r
JOIN role_permissions rp ON r.id = rp.role_id
JOIN permissions p ON rp.permission_id = p.id;

-- 查看管理员的完整权限
SELECT u.username, p.name as permission
FROM users u
JOIN user_roles ur ON u.id = ur.user_id
JOIN roles r ON ur.role_id = r.id
JOIN role_permissions rp ON r.id = rp.role_id
JOIN permissions p ON rp.permission_id = p.id
WHERE u.username = 'admin';
```

## 下一步

参考 `AUTH_README.md` 了解系统架构和开发指南。

