# 统一打包指南

本项目支持将前后端打包成一个整体，并支持在打包时配置服务。

## 功能特性

- ✅ 前后端一体化打包：前端构建产物自动集成到后端 JAR 包中
- ✅ 配置服务支持：打包时可通过配置文件或环境变量配置数据库、端口等
- ✅ 跨平台支持：提供 PowerShell 和 Bash 脚本
- ✅ 自动构建：Maven 打包时自动执行前端构建

## 快速开始

### 方式一：使用打包脚本（推荐）

#### Windows (PowerShell)

```powershell
# 使用默认配置
.\build-with-config.ps1

# 使用配置文件
.\build-with-config.ps1 -ConfigFile config.env

# 指定环境
.\build-with-config.ps1 -Profile prod -ConfigFile config.env
```

#### Linux/Mac (Bash)

```bash
# 添加执行权限（首次使用）
chmod +x build-with-config.sh

# 使用默认配置
./build-with-config.sh

# 使用配置文件
./build-with-config.sh -c config.env

# 指定环境
./build-with-config.sh -p prod -c config.env
```

### 方式二：使用 Maven 直接打包

```bash
cd backend
mvn clean package -DskipTests
```

Maven 打包时会自动：
1. 执行前端构建（`npm run build`）
2. 将前端构建产物复制到 `backend/src/main/resources/static/`
3. 打包成 JAR 文件

## 配置说明

### 创建配置文件

1. 复制配置示例文件：
   ```bash
   cp config.env.example config.env
   ```

2. 编辑 `config.env`，设置你的配置：
   ```properties
   # 数据库配置
   DB_HOST=localhost
   DB_PORT=5432
   DB_NAME=erp_db
   DB_USERNAME=postgres
   DB_PASSWORD=your_password

   # 服务器配置
   SERVER_PORT=8080
   ```

3. 使用配置文件打包：
   ```powershell
   .\build-with-config.ps1 -ConfigFile config.env
   ```

### 配置项说明

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| `DB_HOST` | 数据库主机地址 | `localhost` |
| `DB_PORT` | 数据库端口 | `5432` |
| `DB_NAME` | 数据库名称 | `erp_db` |
| `DB_USERNAME` | 数据库用户名 | `postgres` |
| `DB_PASSWORD` | 数据库密码 | 无（必须设置） |
| `SERVER_PORT` | 服务器端口 | `8080` |
| `JWT_SECRET` | JWT 密钥 | 默认值 |
| `JWT_EXPIRATION` | JWT 过期时间（毫秒） | `86400000` (24小时) |

### 环境变量方式

也可以直接设置环境变量：

```powershell
# Windows PowerShell
$env:DB_HOST = "localhost"
$env:DB_PORT = "5432"
$env:DB_NAME = "erp_db"
$env:DB_USERNAME = "postgres"
$env:DB_PASSWORD = "your_password"
$env:SERVER_PORT = "8080"
.\build-with-config.ps1
```

```bash
# Linux/Mac
export DB_HOST=localhost
export DB_PORT=5432
export DB_NAME=erp_db
export DB_USERNAME=postgres
export DB_PASSWORD=your_password
export SERVER_PORT=8080
./build-with-config.sh
```

## 打包结果

打包完成后，JAR 文件位置：
```
backend/target/erp-0.0.1-SNAPSHOT.jar
```

## 运行应用

### 使用默认配置运行

```bash
java -jar backend/target/erp-0.0.1-SNAPSHOT.jar
```

### 使用外部配置文件运行

```bash
java -jar backend/target/erp-0.0.1-SNAPSHOT.jar \
  --spring.config.location=file:./application.yaml
```

### 使用环境变量运行

```bash
DB_HOST=localhost \
DB_PORT=5432 \
DB_NAME=erp_db \
DB_USERNAME=postgres \
DB_PASSWORD=your_password \
SERVER_PORT=8080 \
java -jar backend/target/erp-0.0.1-SNAPSHOT.jar
```

## 访问应用

打包后的应用可以通过以下地址访问：

- **前端界面**: http://localhost:8080
- **API 接口**: http://localhost:8080/api
- **健康检查**: http://localhost:8080/actuator/health

## 技术实现

### 前端构建配置

前端构建产物会自动输出到：
```
backend/src/main/resources/static/
```

配置位置：`frontend/vite.config.ts`

### 后端静态资源服务

Spring Boot 会自动提供 `classpath:/static/` 下的静态资源。

配置位置：`backend/src/main/java/com/sambound/erp/config/WebConfig.java`

### Maven 构建流程

1. **generate-resources 阶段**：执行前端构建（`exec-maven-plugin`）
2. **process-resources 阶段**：处理配置文件（资源过滤）
3. **compile 阶段**：编译 Java 代码
4. **package 阶段**：打包成 JAR

配置位置：`backend/pom.xml`

## 注意事项

1. **Node.js 和 npm**：确保已安装 Node.js (>=20.19.0) 和 npm
2. **Maven**：确保已安装 Maven
3. **数据库密码**：打包时务必设置正确的数据库密码
4. **前端 API 地址**：前端打包后，API 地址会自动使用相对路径，无需修改
5. **静态资源缓存**：静态资源设置了 1 小时缓存，生产环境可能需要调整

## 故障排除

### 前端构建失败

- 检查 Node.js 版本：`node --version`
- 清理并重新安装依赖：`cd frontend && rm -rf node_modules && npm install`
- 检查 `frontend/package.json` 中的依赖

### 后端构建失败

- 检查 Maven 版本：`mvn --version`
- 清理 Maven 缓存：`mvn clean`
- 检查 Java 版本：`java -version`（需要 Java 25）

### 运行时找不到静态资源

- 确认前端已成功构建到 `backend/src/main/resources/static/`
- 检查 `WebConfig.java` 配置是否正确
- 查看应用日志确认资源路径

## 开发环境

开发环境仍然可以分别运行前后端：

- **前端开发**: `cd frontend && npm run dev`
- **后端开发**: `cd backend && mvn spring-boot:run`

打包配置不会影响开发环境的使用。

