@echo off
REM ============================================
REM ERP系统数据库重建脚本（Windows版本）
REM 版本: 1.0
REM 说明: 完全重建数据库（删除后重新创建）
REM ============================================

setlocal enabledelayedexpansion

REM 数据库配置
set DB_NAME=erp_db
set DB_USER=postgres
set DB_HOST=localhost
set DB_PORT=5432

REM 脚本目录
set SCRIPT_DIR=%~dp0
set SCHEMA_FILE=%SCRIPT_DIR%migration\V1__init_schema.sql
set DATA_FILE=%SCRIPT_DIR%migration\V2__init_data.sql

echo ========================================
echo ERP系统数据库重建脚本
echo ========================================
echo.

REM 检查PostgreSQL是否运行
echo [1/5] 检查PostgreSQL连接...
psql -h %DB_HOST% -p %DB_PORT% -U %DB_USER% -c "SELECT version();" >nul 2>&1
if errorlevel 1 (
    echo [错误] 无法连接到PostgreSQL
    echo 请确保:
    echo   1. PostgreSQL服务正在运行
    echo   2. 数据库用户 %DB_USER% 存在
    echo   3. psql命令在PATH中
    pause
    exit /b 1
)
echo [成功] PostgreSQL连接成功
echo.

REM 检查数据库是否存在
psql -h %DB_HOST% -p %DB_PORT% -U %DB_USER% -lqt | findstr /C:"%DB_NAME%" >nul
if errorlevel 1 (
    echo [2/5] 数据库 %DB_NAME% 不存在
    echo.
    goto :create_db
)

echo [2/5] 数据库 %DB_NAME% 已存在
set /p DELETE_DB="是否删除现有数据库? (y/N): "
if /i not "!DELETE_DB!"=="y" (
    echo 操作已取消
    pause
    exit /b 0
)

REM 删除数据库
echo [3/5] 删除现有数据库...
psql -h %DB_HOST% -p %DB_PORT% -U %DB_USER% -c "DROP DATABASE IF EXISTS %DB_NAME%;"
if errorlevel 1 (
    echo [错误] 删除数据库失败
    pause
    exit /b 1
)
echo [成功] 数据库已删除
echo.

:create_db
REM 创建数据库
echo [4/5] 创建新数据库...
psql -h %DB_HOST% -p %DB_PORT% -U %DB_USER% -c "CREATE DATABASE %DB_NAME%;"
if errorlevel 1 (
    echo [错误] 创建数据库失败
    pause
    exit /b 1
)
echo [成功] 数据库已创建
echo.

REM 执行Schema脚本
echo [5/5] 执行Schema脚本...
if not exist "%SCHEMA_FILE%" (
    echo [错误] Schema文件不存在: %SCHEMA_FILE%
    pause
    exit /b 1
)

psql -h %DB_HOST% -p %DB_PORT% -U %DB_USER% -d %DB_NAME% -f "%SCHEMA_FILE%"
if errorlevel 1 (
    echo [错误] Schema脚本执行失败
    pause
    exit /b 1
)
echo [成功] Schema脚本执行成功
echo.

REM 执行Data脚本
echo [6/6] 执行数据脚本...
if not exist "%DATA_FILE%" (
    echo [错误] 数据文件不存在: %DATA_FILE%
    pause
    exit /b 1
)

psql -h %DB_HOST% -p %DB_PORT% -U %DB_USER% -d %DB_NAME% -f "%DATA_FILE%"
if errorlevel 1 (
    echo [错误] 数据脚本执行失败
    pause
    exit /b 1
)
echo [成功] 数据脚本执行成功
echo.

REM 验证数据
echo [验证] 检查数据库内容...
psql -h %DB_HOST% -p %DB_PORT% -U %DB_USER% -d %DB_NAME% -c "SELECT '用户表' AS 表名, COUNT(*) AS 记录数 FROM users UNION ALL SELECT '角色表', COUNT(*) FROM roles UNION ALL SELECT '权限表', COUNT(*) FROM permissions UNION ALL SELECT '审计日志表', COUNT(*) FROM audit_logs;"

echo.
echo ========================================
echo 数据库重建完成！
echo ========================================
echo.
echo 默认账户信息:
echo   用户名: admin
echo   密码: admin123
echo   角色: ADMIN
echo.
echo 测试账户信息:
echo   用户名: testuser
echo   密码: admin123
echo   角色: USER
echo.
pause

