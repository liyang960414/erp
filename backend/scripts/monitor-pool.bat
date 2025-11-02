@echo off
REM HikariCP 连接池监控脚本 (Windows)
REM 使用方法: monitor-pool.bat [token] [api_url]

setlocal enabledelayedexpansion

REM 配置
set TOKEN=%1
set API_URL=%2
if "%API_URL%"=="" set API_URL=http://localhost:8080/api/monitor/pool
set SUMMARY_URL=%API_URL%/summary

REM 检查 token
if "%TOKEN%"=="" (
    echo 错误: 请提供 JWT token
    echo 使用方法: %0 ^<token^> [api_url]
    echo 示例: %0 eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
    exit /b 1
)

REM 获取连接池摘要
echo 正在获取连接池状态...
curl -s -H "Authorization: Bearer %TOKEN%" "%SUMMARY_URL%" > temp_response.json

REM 检查响应
if not exist temp_response.json (
    echo 错误: 无法连接到服务器
    exit /b 1
)

REM 显示原始 JSON（简单版本，Windows 下解析 JSON 较复杂）
echo.
echo ========================================
echo     连接池状态监控
echo ========================================
type temp_response.json
echo ========================================
echo.

REM 提示用户查看完整文档获取更多信息
echo 提示: 查看 POOL_MONITORING.md 获取详细监控说明
echo.

REM 清理临时文件
del temp_response.json 2>nul

endlocal

