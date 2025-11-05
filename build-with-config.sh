#!/bin/bash
# 统一打包脚本 - 支持配置服务
# 用法: ./build-with-config.sh [-p dev|prod] [-c config.env]

set -e

PROFILE="prod"
CONFIG_FILE=""

# 解析命令行参数
while [[ $# -gt 0 ]]; do
    case $1 in
        -p|--profile)
            PROFILE="$2"
            shift 2
            ;;
        -c|--config)
            CONFIG_FILE="$2"
            shift 2
            ;;
        *)
            echo "未知参数: $1"
            exit 1
            ;;
    esac
done

echo "========================================"
echo "ERP 统一打包脚本"
echo "========================================"
echo ""

# 检查 Node.js 和 npm
echo "检查 Node.js 环境..."
if ! command -v node &> /dev/null; then
    echo "✗ 未找到 Node.js，请先安装 Node.js"
    exit 1
fi
if ! command -v npm &> /dev/null; then
    echo "✗ 未找到 npm，请先安装 npm"
    exit 1
fi
echo "✓ Node.js: $(node --version)"
echo "✓ npm: $(npm --version)"

# 检查 Maven
echo "检查 Maven 环境..."
if ! command -v mvn &> /dev/null; then
    echo "✗ 未找到 Maven，请先安装 Maven"
    exit 1
fi
echo "✓ Maven: $(mvn --version | head -n 1)"

# 加载配置文件
if [ -n "$CONFIG_FILE" ] && [ -f "$CONFIG_FILE" ]; then
    echo "加载配置文件: $CONFIG_FILE"
    set -a
    source "$CONFIG_FILE"
    set +a
else
    echo "使用默认配置或环境变量"
fi

# 设置默认配置（如果未提供）
export DB_HOST=${DB_HOST:-localhost}
export DB_PORT=${DB_PORT:-5432}
export DB_NAME=${DB_NAME:-erp_db}
export DB_USERNAME=${DB_USERNAME:-postgres}
export DB_PASSWORD=${DB_PASSWORD:-}
export SERVER_PORT=${SERVER_PORT:-8080}

echo ""
echo "当前配置:"
echo "  数据库: $DB_HOST:$DB_PORT/$DB_NAME"
echo "  用户名: $DB_USERNAME"
echo "  服务端口: $SERVER_PORT"
echo ""

# 构建前端
echo "========================================"
echo "步骤 1/3: 构建前端..."
echo "========================================"
cd frontend

# 检查并安装依赖
if [ ! -d "node_modules" ]; then
    echo "安装前端依赖..."
    npm install
fi

# 构建前端
echo "执行前端构建..."
npm run build

if [ $? -ne 0 ]; then
    echo "✗ 前端构建失败"
    exit 1
fi
echo "✓ 前端构建完成"

cd ..

echo ""

# 构建后端
echo "========================================"
echo "步骤 2/3: 构建后端..."
echo "========================================"
cd backend

# 准备配置文件
CONFIG_FILE_PATH="src/main/resources/application.yaml"
TEMPLATE_FILE="src/main/resources/application-template.yaml"

if [ -f "$TEMPLATE_FILE" ]; then
    echo "从模板生成配置文件..."
    sed -e "s|\${db.host:localhost}|$DB_HOST|g" \
        -e "s|\${db.port:5432}|$DB_PORT|g" \
        -e "s|\${db.name:erp_db}|$DB_NAME|g" \
        -e "s|\${db.username:postgres}|$DB_USERNAME|g" \
        -e "s|\${db.password:}|$DB_PASSWORD|g" \
        -e "s|\${server.port:8080}|$SERVER_PORT|g" \
        "$TEMPLATE_FILE" > "$CONFIG_FILE_PATH"
    echo "✓ 配置文件已生成"
fi

# 执行 Maven 打包（会自动执行前端构建）
echo "执行 Maven 打包..."
mvn clean package -DskipTests

if [ $? -ne 0 ]; then
    echo "✗ 后端构建失败"
    exit 1
fi
echo "✓ 后端构建完成"

cd ..

echo ""
echo "========================================"
echo "步骤 3/3: 打包完成"
echo "========================================"
echo ""

JAR_FILE="backend/target/erp-0.0.1-SNAPSHOT.jar"
if [ -f "$JAR_FILE" ]; then
    JAR_SIZE=$(du -h "$JAR_FILE" | cut -f1)
    echo "✓ 打包成功!"
    echo ""
    echo "JAR 文件位置: $JAR_FILE"
    echo "文件大小: $JAR_SIZE"
    echo ""
    echo "运行命令:"
    echo "  java -jar $JAR_FILE"
    echo ""
    echo "或使用配置文件:"
    echo "  java -jar $JAR_FILE --spring.config.location=classpath:/application.yaml"
else
    echo "✗ 未找到 JAR 文件"
    exit 1
fi

