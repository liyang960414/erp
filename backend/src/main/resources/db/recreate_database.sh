#!/bin/bash
# ============================================
# ERP系统数据库重建脚本
# 版本: 1.0
# 说明: 完全重建数据库（删除后重新创建）
# ============================================

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 数据库配置
DB_NAME="erp_db"
DB_USER="postgres"
DB_HOST="localhost"
DB_PORT="5432"

# 脚本目录
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SCHEMA_FILE="$SCRIPT_DIR/migration/V1__init_schema.sql"
DATA_FILE="$SCRIPT_DIR/migration/V2__init_data.sql"

echo -e "${YELLOW}========================================${NC}"
echo -e "${YELLOW}ERP系统数据库重建脚本${NC}"
echo -e "${YELLOW}========================================${NC}"
echo ""

# 检查PostgreSQL是否运行
echo -e "${YELLOW}[1/5] 检查PostgreSQL连接...${NC}"
if ! PGPASSWORD=$PGPASSWORD psql -h $DB_HOST -p $DB_PORT -U $DB_USER -l > /dev/null 2>&1; then
    echo -e "${RED}错误: 无法连接到PostgreSQL${NC}"
    echo "请确保:"
    echo "  1. PostgreSQL服务正在运行"
    echo "  2. 数据库用户 $DB_USER 存在"
    echo "  3. PGPASSWORD环境变量已设置"
    exit 1
fi
echo -e "${GREEN}✓ PostgreSQL连接成功${NC}"
echo ""

# 检查数据库是否存在
if PGPASSWORD=$PGPASSWORD psql -h $DB_HOST -p $DB_PORT -U $DB_USER -lqt | cut -d \| -f 1 | grep -qw $DB_NAME; then
    echo -e "${YELLOW}[2/5] 数据库 $DB_NAME 已存在${NC}"
    read -p "是否删除现有数据库? (y/N): " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        echo -e "${YELLOW}操作已取消${NC}"
        exit 0
    fi
    
    # 删除数据库
    echo -e "${YELLOW}[3/5] 删除现有数据库...${NC}"
    PGPASSWORD=$PGPASSWORD psql -h $DB_HOST -p $DB_PORT -U $DB_USER -c "DROP DATABASE IF EXISTS $DB_NAME;"
    if [ $? -ne 0 ]; then
        echo -e "${RED}错误: 删除数据库失败${NC}"
        exit 1
    fi
    echo -e "${GREEN}✓ 数据库已删除${NC}"
    echo ""
else
    echo -e "${GREEN}[2/5] 数据库 $DB_NAME 不存在${NC}"
    echo ""
fi

# 创建数据库
echo -e "${YELLOW}[3/5] 创建新数据库...${NC}"
PGPASSWORD=$PGPASSWORD psql -h $DB_HOST -p $DB_PORT -U $DB_USER -c "CREATE DATABASE $DB_NAME;"
if [ $? -ne 0 ]; then
    echo -e "${RED}错误: 创建数据库失败${NC}"
    exit 1
fi
echo -e "${GREEN}✓ 数据库已创建${NC}"
echo ""

# 执行Schema脚本
echo -e "${YELLOW}[4/5] 执行Schema脚本...${NC}"
if [ ! -f "$SCHEMA_FILE" ]; then
    echo -e "${RED}错误: Schema文件不存在: $SCHEMA_FILE${NC}"
    exit 1
fi

PGPASSWORD=$PGPASSWORD psql -h $DB_HOST -p $DB_PORT -U $DB_USER -d $DB_NAME -f "$SCHEMA_FILE"
if [ $? -ne 0 ]; then
    echo -e "${RED}错误: Schema脚本执行失败${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Schema脚本执行成功${NC}"
echo ""

# 执行Data脚本
echo -e "${YELLOW}[5/5] 执行数据脚本...${NC}"
if [ ! -f "$DATA_FILE" ]; then
    echo -e "${RED}错误: 数据文件不存在: $DATA_FILE${NC}"
    exit 1
fi

PGPASSWORD=$PGPASSWORD psql -h $DB_HOST -p $DB_PORT -U $DB_USER -d $DB_NAME -f "$DATA_FILE"
if [ $? -ne 0 ]; then
    echo -e "${RED}错误: 数据脚本执行失败${NC}"
    exit 1
fi
echo -e "${GREEN}✓ 数据脚本执行成功${NC}"
echo ""

# 验证数据
echo -e "${YELLOW}[验证] 检查数据库内容...${NC}"
PGPASSWORD=$PGPASSWORD psql -h $DB_HOST -p $DB_PORT -U $DB_USER -d $DB_NAME -c "
SELECT 
    '用户表' AS 表名, COUNT(*) AS 记录数 FROM users
UNION ALL
SELECT '角色表', COUNT(*) FROM roles
UNION ALL
SELECT '权限表', COUNT(*) FROM permissions;
"

echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}数据库重建完成！${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo -e "${YELLOW}默认账户信息:${NC}"
echo "  用户名: admin"
echo "  密码: admin123"
echo "  角色: ADMIN"
echo ""
echo -e "${YELLOW}测试账户信息:${NC}"
echo "  用户名: testuser"
echo "  密码: admin123"
echo "  角色: USER"
echo ""

