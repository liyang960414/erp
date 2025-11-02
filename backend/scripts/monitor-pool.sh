#!/bin/bash

# HikariCP 连接池监控脚本
# 使用方法: ./monitor-pool.sh [token] [api_url]

# 配置
TOKEN="${1:-}"
API_URL="${2:-http://localhost:8080/api/monitor/pool}"
SUMMARY_URL="${API_URL}/summary"
STATUS_URL="${API_URL}/status"

# 颜色定义
RED='\033[0;31m'
YELLOW='\033[1;33m'
GREEN='\033[0;32m'
NC='\033[0m' # No Color

# 检查 token
if [ -z "$TOKEN" ]; then
    echo -e "${RED}错误: 请提供 JWT token${NC}"
    echo "使用方法: $0 <token> [api_url]"
    echo "示例: $0 eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
    exit 1
fi

# 获取连接池摘要
echo "正在获取连接池状态..."
response=$(curl -s -w "\n%{http_code}" -H "Authorization: Bearer $TOKEN" "$SUMMARY_URL")
http_code=$(echo "$response" | tail -n1)
body=$(echo "$response" | sed '$d')

if [ "$http_code" != "200" ]; then
    echo -e "${RED}错误: HTTP $http_code${NC}"
    echo "$body"
    exit 1
fi

# 解析 JSON (需要 jq，如果没有则使用 grep)
if command -v jq &> /dev/null; then
    active=$(echo "$body" | jq -r '.activeConnections')
    idle=$(echo "$body" | jq -r '.idleConnections')
    total=$(echo "$body" | jq -r '.totalConnections')
    max=$(echo "$body" | jq -r '.maxPoolSize')
    waiting=$(echo "$body" | jq -r '.waitingThreads')
    usage_rate=$(echo "$body" | jq -r '.usageRate')
else
    # 使用 grep 和 sed 解析（不依赖 jq）
    active=$(echo "$body" | grep -o '"activeConnections":[0-9]*' | cut -d':' -f2)
    idle=$(echo "$body" | grep -o '"idleConnections":[0-9]*' | cut -d':' -f2)
    total=$(echo "$body" | grep -o '"totalConnections":[0-9]*' | cut -d':' -f2)
    max=$(echo "$body" | grep -o '"maxPoolSize":[0-9]*' | cut -d':' -f2)
    waiting=$(echo "$body" | grep -o '"waitingThreads":[0-9]*' | cut -d':' -f2)
    usage_rate=$(echo "$body" | grep -o '"usageRate":"[^"]*' | cut -d'"' -f4)
fi

# 计算使用率百分比（数字）
usage_num=$(echo "$usage_rate" | sed 's/%//' | cut -d'.' -f1)

# 显示状态
echo ""
echo "========================================"
echo "    连接池状态监控"
echo "========================================"
echo "活跃连接:    $active / $max"
echo "空闲连接:    $idle"
echo "总连接数:    $total"
echo "等待线程:    $waiting"
echo "使用率:      $usage_rate"
echo "========================================"
echo ""

# 状态判断
status=0
if [ "$waiting" -gt 0 ]; then
    echo -e "${RED}⚠ 警告: 有 $waiting 个线程正在等待获取连接！${NC}"
    status=1
fi

if [ -n "$usage_num" ] && [ "$usage_num" -gt 90 ]; then
    echo -e "${RED}⚠ 警告: 连接池使用率过高 ($usage_rate)！${NC}"
    status=1
elif [ -n "$usage_num" ] && [ "$usage_num" -gt 75 ]; then
    echo -e "${YELLOW}⚠ 注意: 连接池使用率较高 ($usage_rate)${NC}"
elif [ -n "$usage_num" ]; then
    echo -e "${GREEN}✓ 连接池状态正常${NC}"
fi

# 提供建议
if [ "$usage_num" -gt 90 ]; then
    echo ""
    echo "建议:"
    echo "  1. 检查是否存在连接泄漏"
    echo "  2. 考虑增加连接池大小"
    echo "  3. 查看详细日志: tail -f logs/erp.log"
fi

if [ "$waiting" -gt 0 ]; then
    echo ""
    echo "建议:"
    echo "  1. 检查是否有长时间运行的事务"
    echo "  2. 查看是否有连接未正确释放"
    echo "  3. 考虑增加连接池大小"
fi

exit $status

