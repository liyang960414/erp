# 快速开始指南

## 系统要求

- Java 25
- Maven 3.6+
- PostgreSQL 12+
- 至少2GB可用内存

## 快速部署步骤

### 1. 准备数据库

确保PostgreSQL服务正在运行，然后创建数据库：

```bash
# 连接到PostgreSQL
psql -U postgres

# 创建数据库
CREATE DATABASE erp_db;

# 退出
\q
```

### 2. 配置数据库连接（可选）

如果需要修改数据库连接，编辑 `src/main/resources/application.yaml`：

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/erp_db
    username: your_username
    password: your_password
```

### 3. 编译和运行

```bash
# 进入backend目录
cd backend

# 使用Maven编译
./mvnw clean install

# 运行应用
./mvnw spring-boot:run

# Windows用户使用
mvnw.cmd spring-boot:run
```

或者使用IDE直接运行 `ErpApplication.java`

### 4. 验证安装

应用启动后，你会看到类似以下的日志：

```
Started ErpApplication in X.XXX seconds
```

### 5. 测试登录

打开浏览器或使用curl访问：

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'
```

如果返回JWT token，说明系统运行正常！

## 默认账户

系统自动创建的管理员账户：
- **用户名**: admin
- **密码**: admin123
- **角色**: ADMIN（拥有所有权限）

## 下一步

1. 查看 `AUTH_README.md` 了解系统架构
2. 查看 `API_TEST_EXAMPLES.md` 学习API使用
3. 阅读代码注释了解实现细节

## 常见问题

### 端口被占用

如果8080端口被占用，修改 `application.yaml`：

```yaml
server:
  port: 8081
```

### 数据库连接失败

1. 确保PostgreSQL正在运行
2. 检查用户名和密码
3. 确保防火墙允许连接

### 依赖下载失败

尝试使用国内镜像，编辑 `~/.m2/settings.xml`（没有则创建）：

```xml
<settings>
  <mirrors>
    <mirror>
      <id>aliyun</id>
      <name>Aliyun Maven Repository</name>
      <url>https://maven.aliyun.com/repository/public</url>
      <mirrorOf>central</mirrorOf>
    </mirror>
  </mirrors>
</settings>
```

### 编译错误

确保使用Java 25：

```bash
java -version
```

应该显示：
```
openjdk version "25" ...
```

## 开发建议

1. 使用IDE（推荐IntelliJ IDEA或Eclipse）
2. 启用热重载功能
3. 使用Postman或Insomnia测试API
4. 查看应用日志排查问题

## 性能优化

生产环境建议：

1. 使用连接池优化数据库连接
2. 配置JWT token过期时间
3. 启用缓存机制
4. 配置日志级别
5. 使用HTTPS

## 安全建议

1. 修改默认管理员密码
2. 使用强密码策略
3. 配置HTTPS
4. 限制API访问频率
5. 定期审计用户权限

## 获取帮助

- 查看日志文件定位问题
- 检查Spring Boot文档
- 参考Spring Security文档
- 查看JWT官方文档

