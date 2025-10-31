# ERP系统

企业资源计划(ERP)系统，基于Spring Boot 4和Vue 3开发。

## 技术栈

### 后端
- **Spring Boot 4.0.0-RC1** - Java应用框架
- **Spring Security** - 安全框架
- **Spring Data JPA** - 数据访问层
- **PostgreSQL** - 关系型数据库
- **JWT** - 身份验证令牌
- **Java 25** - 编程语言
- **Maven** - 项目构建工具

### 前端
- **Vue 3** - 渐进式JavaScript框架
- **TypeScript** - 类型安全的JavaScript
- **Vite** - 前端构建工具

## 项目结构

```
erp/
├── backend/          # 后端服务
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/
│   │   │   │   └── com/sambound/erp/
│   │   │   │       ├── config/        # 配置类
│   │   │   │       ├── controller/    # 控制器
│   │   │   │       ├── dto/           # 数据传输对象
│   │   │   │       ├── entity/        # 实体类
│   │   │   │       ├── exception/     # 异常处理
│   │   │   │       ├── repository/    # 数据访问层
│   │   │   │       └── service/       # 业务逻辑层
│   │   │   └── resources/
│   │   │       ├── application.yaml   # 配置文件
│   │   │       └── db/                # 数据库脚本
│   │   │           ├── migration/     # 迁移脚本
│   │   │           │   ├── V1__init_schema.sql
│   │   │           │   └── V2__init_data.sql
│   │   │           ├── recreate_database.sh  # 重建脚本(Linux/Mac)
│   │   │           ├── recreate_database.bat # 重建脚本(Windows)
│   │   │           └── drop_all_tables.sql   # 删除所有表
│   │   └── test/                      # 测试代码
│   ├── pom.xml                        # Maven配置
│   ├── AUTH_README.md                 # 认证系统文档
│   ├── API_TEST_EXAMPLES.md           # API测试示例
│   └── QUICKSTART.md                  # 快速开始指南
├── frontend/        # 前端应用
│   ├── src/
│   │   ├── components/    # Vue组件
│   │   ├── views/         # 视图
│   │   ├── router/        # 路由配置
│   │   └── stores/        # 状态管理
│   └── package.json       # NPM配置
└── README.md             # 项目说明
```

## 功能特性

### 用户认证系统
- ✅ JWT身份验证
- ✅ 用户注册和登录
- ✅ 密码加密存储
- ✅ 基于角色的权限控制(RBAC)
- ✅ 细粒度权限管理
- ✅ API安全防护

### 已实现功能
- 用户管理
- 角色管理
- 权限管理
- RESTful API
- 全局异常处理
- 数据验证

### 规划中功能
- 商品管理
- 订单管理
- 库存管理
- 报表统计
- 系统设置

## 快速开始

### 前置条件

1. **Java 25** - [下载地址](https://www.oracle.com/java/)
2. **PostgreSQL 12+** - [下载地址](https://www.postgresql.org/download/)
3. **Maven 3.6+** - [下载地址](https://maven.apache.org/)
4. **Node.js 18+** - [下载地址](https://nodejs.org/)（用于前端）

### 后端启动

#### 方式一：使用数据库脚本（推荐）

1. 创建数据库：
```sql
CREATE DATABASE erp_db;
```

2. 执行初始化脚本：

**Linux/Mac:**
```bash
cd backend/src/main/resources/db
chmod +x recreate_database.sh
export PGPASSWORD=postgres  # 设置PostgreSQL密码
./recreate_database.sh
```

**Windows:**
```batch
cd backend\src\main\resources\db
recreate_database.bat
```

#### 方式二：使用Spring Boot自动初始化

1. 创建数据库：
```sql
CREATE DATABASE erp_db;
```

2. Spring Boot会自动创建表（ddl-auto: update）

3. 应用启动时会通过`DataInitializer.java`自动插入初始数据

4. 启动后端服务：
```bash
cd backend
./mvnw spring-boot:run
```

### 前端启动

```bash
cd frontend
npm install
npm run dev
```

访问 http://localhost:5173

### 默认账户

- **用户名**: admin
- **密码**: admin123
- **角色**: 管理员

## 文档

### 后端文档
- [快速开始指南](backend/QUICKSTART.md) - 部署和配置说明
- [认证系统文档](backend/AUTH_README.md) - 完整的认证和权限系统说明
- [API测试示例](backend/API_TEST_EXAMPLES.md) - API使用示例和测试方法
- [数据库脚本说明](backend/src/main/resources/db/README.md) - 数据库初始化和维护脚本

### 前端文档
- [前端使用指南](frontend/README.md) - 前端开发和使用说明

## API文档

### 认证接口

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| POST | /api/auth/register | 用户注册 | 公开 |
| POST | /api/auth/login | 用户登录 | 公开 |

### 用户接口

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | /api/users/me | 获取当前用户 | 认证 |
| GET | /api/users/{id} | 获取指定用户 | ADMIN |

更多API文档请参考 [API_TEST_EXAMPLES.md](backend/API_TEST_EXAMPLES.md)

## 开发规范

### 代码规范
- 严格遵循三层架构：Controller、Service、Repository
- 使用Java Record作为DTO
- 使用构造函数注入依赖
- 使用JPA和Spring Data Repository

### 安全规范
- 所有密码使用BCrypt加密
- JWT token设置合理过期时间
- 敏感信息使用环境变量
- 生产环境启用HTTPS

### API规范
- RESTful风格设计
- 使用统一响应格式
- 合理的HTTP状态码
- 详细的错误信息

## 测试

### 后端测试
```bash
cd backend
./mvnw test
```

### API测试
使用Postman导入测试集合，或参考 [API_TEST_EXAMPLES.md](backend/API_TEST_EXAMPLES.md)

## 贡献指南

1. Fork本项目
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 开启Pull Request

## 许可证

本项目采用 MIT 许可证

## 联系方式

- 项目主页: [GitHub](https://github.com/yourusername/erp)
- 问题反馈: [Issues](https://github.com/yourusername/erp/issues)

## 更新日志

### v0.1.0 (2024-01-01)
- ✅ 集成PostgreSQL数据库
- ✅ 实现用户认证系统
- ✅ 实现基于角色的权限管理
- ✅ 添加JWT身份验证
- ✅ 创建RESTful API
- ✅ 添加全局异常处理
- ✅ 提供数据库初始化和维护脚本

## 致谢

感谢所有为本项目做出贡献的开发者和开源社区！
