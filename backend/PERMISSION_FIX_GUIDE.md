# 权限问题修复指南

## 问题描述
即使数据库中的用户角色和权限配置正确，登录后仍提示"没有访问权限"。

## 根本原因
`Role` 实体中的 `permissions` 使用 `FetchType.LAZY` 延迟加载。当 Spring Security 验证权限时：
1. 用户对象被加载，`roles` 通过 EAGER 加载
2. 但 `roles.permissions` 是 LAZY 的，在某些情况下（如过滤器链）没有事务上下文，导致权限无法加载
3. `User.getAuthorities()` 返回空集合，Spring Security 认为用户没有任何权限

## 修复方案
使用 JPA `JOIN FETCH` 在查询时一次性加载所有关联数据：
- 在 `UserRepository` 中添加 `findByUsernameWithRolesAndPermissions()` 方法
- 在 `UserDetailsServiceImpl`、`AuthService`、`UserService` 中使用该方法

## 验证步骤

### 1. 重新启动后端服务
```bash
# 停止当前运行的后端服务
# 重新启动后端服务
cd backend
./mvnw spring-boot:run
# 或
java -jar target/erp-*.jar
```

### 2. 清除浏览器缓存和LocalStorage
打开浏览器开发者工具（F12）：
- 清除 LocalStorage 中的 `token` 和 `user`
- 或直接使用隐私模式/无痕模式

### 3. 重新登录
- 访问登录页面
- 使用 admin/admin123 登录
- 登录成功后，系统应该能正常访问

### 4. 验证权限加载
在浏览器控制台执行以下代码检查用户信息：
```javascript
const user = JSON.parse(localStorage.getItem('user'));
console.log('用户角色:', user?.roles);
console.log('角色权限:', user?.roles?.[0]?.permissions);
```

### 5. 后端日志验证
查看后端日志，应该看到类似信息：
- 用户登录成功
- JWT token 生成成功
- 用户信息查询成功

### 6. 测试权限检查
尝试访问需要 ADMIN 权限的页面，如：
- `/users/list` - 用户列表
- `/system/settings` - 系统设置
- `/system/roles` - 角色管理

## 如果问题仍然存在

### 检查1：确认数据库中的数据正确
执行诊断脚本：
```bash
psql -h localhost -U postgres -d erp_db -f backend/src/main/resources/db/check_admin_permissions.sql
```

### 检查2：查看后端日志
检查是否有以下错误：
- LazyInitializationException
- 权限相关的警告或错误

### 检查3：验证 JWT Token
在登录后，检查 JWT token 是否包含正确的用户信息（虽然角色不在 token 中，但在服务器端验证时会加载）

### 检查4：清除旧的用户会话
如果问题持续，可能需要：
1. 重新初始化数据库
2. 清除所有浏览器数据
3. 重启后端服务

## 技术细节

### 修改的文件
1. `UserRepository.java` - 添加 JOIN FETCH 查询方法
2. `UserDetailsServiceImpl.java` - 使用 JOIN FETCH 加载用户（JWT认证）
3. `AuthService.java` - 使用 JOIN FETCH 加载用户（登录验证）
4. `UserService.java` - 使用 JOIN FETCH 加载用户（API返回）

### JOIN FETCH 查询
```java
@Query("SELECT DISTINCT u FROM User u " +
       "LEFT JOIN FETCH u.roles r " +
       "LEFT JOIN FETCH r.permissions " +
       "WHERE u.username = :username")
Optional<User> findByUsernameWithRolesAndPermissions(@Param("username") String username);
```

这个查询确保：
- 一次性加载用户、角色和权限
- 避免 N+1 查询问题
- 确保所有权限数据在同一个事务中加载

## 相关文件
- `backend/src/main/java/com/sambound/erp/repository/UserRepository.java`
- `backend/src/main/java/com/sambound/erp/config/UserDetailsServiceImpl.java`
- `backend/src/main/java/com/sambound/erp/service/AuthService.java`
- `backend/src/main/java/com/sambound/erp/service/UserService.java`
- `backend/src/main/java/com/sambound/erp/entity/Role.java`
- `backend/src/main/java/com/sambound/erp/entity/User.java`

