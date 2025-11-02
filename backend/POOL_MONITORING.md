# HikariCP 连接池监控指南

本文档介绍如何监控 HikariCP 连接池的状态，以便及时发现和解决连接泄漏等问题。

## 监控方法

### 1. REST API 监控端点

项目提供了三个 REST API 端点来监控连接池状态：

#### 1.1 获取详细状态
```bash
GET /api/monitor/pool/status
Authorization: Bearer <token>
```

**响应示例：**
```json
{
  "config": {
    "maximumPoolSize": 20,
    "minimumIdle": 10,
    "connectionTimeout": 30000,
    "idleTimeout": 600000,
    "maxLifetime": 1200000,
    "leakDetectionThreshold": 120000
  },
  "runtime": {
    "activeConnections": 5,
    "idleConnections": 5,
    "totalConnections": 10,
    "threadsAwaitingConnection": 0,
    "pendingThreads": 0
  },
  "health": {
    "usageRate": "25.00%",
    "availableConnections": 15,
    "connectionUtilization": "5/20",
    "status": "HEALTHY"
  }
}
```

#### 1.2 获取简要摘要
```bash
GET /api/monitor/pool/summary
Authorization: Bearer <token>
```

**响应示例：**
```json
{
  "activeConnections": 5,
  "idleConnections": 5,
  "totalConnections": 10,
  "maxPoolSize": 20,
  "availableConnections": 15,
  "waitingThreads": 0,
  "usageRate": "25.0%"
}
```

#### 1.3 强制清理空闲连接（紧急情况）
```bash
GET /api/monitor/pool/evict-idle
Authorization: Bearer <token>
```

**使用场景：**
- 连接池出现异常时
- 需要强制释放空闲连接时

**注意：** 仅在紧急情况下使用，正常情况下不需要手动清理。

### 2. 自动日志监控

项目配置了自动日志记录，每 5 分钟记录一次连接池状态。

**日志示例：**
```
INFO  PoolStatusLogger - 连接池状态 - 活跃: 5/20, 空闲: 5, 总数: 10, 等待线程: 0, 使用率: 25.00%
```

**警告示例：**
```
WARN  PoolStatusLogger - 连接池使用率过高: 95.00%, 可能导致连接获取超时
WARN  PoolStatusLogger - 有 3 个线程正在等待获取连接，可能存在连接泄漏或连接池配置不足
```

**日志位置：** `./target/logs/erp.log`

### 3. 启用 HikariCP 详细日志

在 `application.yaml` 中取消注释以下行来启用 HikariCP 的详细日志：

```yaml
logging:
  level:
    com.zaxxer.hikari: DEBUG
```

这将输出连接池的详细操作信息，包括：
- 连接获取和释放
- 连接泄漏检测
- 连接池状态变化

**注意：** 详细日志会产生大量输出，仅在调试时启用。

### 4. 通过 Spring Boot Actuator（可选）

如果需要更全面的监控，可以添加 Spring Boot Actuator 依赖：

#### 4.1 添加依赖

在 `pom.xml` 中添加：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

#### 4.2 配置 Actuator

在 `application.yaml` 中添加：

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,metrics,info
  endpoint:
    health:
      show-details: always
  metrics:
    export:
      prometheus:
        enabled: true  # 可选：启用 Prometheus 监控
```

#### 4.3 访问健康检查

```bash
GET /actuator/health
```

健康检查会自动包含连接池的健康状态。

## 关键指标说明

### 1. 活跃连接数 (activeConnections)
- **含义：** 当前正在使用的连接数
- **正常值：** 应小于最大池大小的 80%
- **异常情况：** 接近或等于最大池大小时，说明连接池可能不足

### 2. 空闲连接数 (idleConnections)
- **含义：** 当前空闲可用的连接数
- **正常值：** 应大于 0
- **异常情况：** 长时间为 0 可能表示连接池压力过大

### 3. 总连接数 (totalConnections)
- **含义：** 活跃连接数 + 空闲连接数
- **正常值：** 应在最小空闲数和最大池大小之间
- **异常情况：** 超过最大池大小表示严重问题

### 4. 等待线程数 (threadsAwaitingConnection)
- **含义：** 正在等待获取连接的线程数
- **正常值：** 应为 0
- **异常情况：** 大于 0 表示连接池不足，可能需要增加池大小或检查连接泄漏

### 5. 使用率 (usageRate)
- **含义：** 活跃连接数 / 最大池大小
- **正常值：** < 75%
- **警告值：** 75% - 90%
- **危险值：** > 90%

## 问题诊断

### 连接泄漏检测

如果出现连接泄漏警告：

1. **检查日志中的泄漏警告：**
   ```
   Exception: Apparent connection leak detected
   ```

2. **查看等待线程数：**
   ```bash
   curl -H "Authorization: Bearer <token>" http://localhost:8080/api/monitor/pool/summary
   ```
   如果 `waitingThreads` 持续大于 0，可能存在连接泄漏。

3. **检查连接使用率：**
   如果使用率持续接近 100%，可能存在连接泄漏。

4. **启用详细日志：**
   取消注释 `com.zaxxer.hikari: DEBUG`，查看连接获取和释放的详细日志。

### 连接池配置优化

如果经常出现连接不足：

1. **增加最大池大小：**
   ```yaml
   spring:
     datasource:
       hikari:
         maximum-pool-size: 30  # 从 20 增加到 30
   ```

2. **增加最小空闲连接数：**
   ```yaml
   spring:
     datasource:
       hikari:
         minimum-idle: 15  # 从 10 增加到 15
   ```

3. **调整连接获取超时时间：**
   ```yaml
   spring:
     datasource:
       hikari:
         connection-timeout: 60000  # 从 30 秒增加到 60 秒
   ```

## 监控最佳实践

1. **定期检查：**
   - 每小时检查一次连接池状态
   - 特别关注高峰期的连接使用情况

2. **设置告警：**
   - 使用率 > 90% 时告警
   - 等待线程数 > 0 时告警
   - 出现连接泄漏时告警

3. **记录基线：**
   - 记录正常业务情况下的连接池使用情况
   - 对比异常情况，找出问题根源

4. **容量规划：**
   - 根据业务增长趋势，提前规划连接池大小
   - 在高峰期预留 20% 的连接余量

## 示例：使用 curl 监控

```bash
# 设置认证 token
TOKEN="your-jwt-token"

# 获取连接池摘要
curl -H "Authorization: Bearer $TOKEN" \
     http://localhost:8080/api/monitor/pool/summary

# 获取详细状态
curl -H "Authorization: Bearer $TOKEN" \
     http://localhost:8080/api/monitor/pool/status

# 检查健康状态（如果启用了 Actuator）
curl http://localhost:8080/actuator/health
```

## 示例：使用脚本监控

创建一个监控脚本 `monitor-pool.sh`：

```bash
#!/bin/bash

TOKEN="your-jwt-token"
API_URL="http://localhost:8080/api/monitor/pool/summary"

response=$(curl -s -H "Authorization: Bearer $TOKEN" "$API_URL")

usage_rate=$(echo "$response" | grep -o '"usageRate":"[^"]*' | cut -d'"' -f4)
waiting=$(echo "$response" | grep -o '"waitingThreads":[0-9]*' | cut -d':' -f2)

echo "连接池使用率: $usage_rate"
echo "等待线程数: $waiting"

if [ "$waiting" -gt 0 ]; then
    echo "警告: 有线程正在等待连接！"
    exit 1
fi

usage_num=$(echo "$usage_rate" | cut -d'%' -f1)
if (( $(echo "$usage_num > 90" | bc -l) )); then
    echo "警告: 连接池使用率过高！"
    exit 1
fi

echo "连接池状态正常"
exit 0
```

使用方法：
```bash
chmod +x monitor-pool.sh
./monitor-pool.sh
```

## 故障排查清单

当出现连接问题时，按以下顺序检查：

- [ ] 检查连接池状态 API，查看当前使用情况
- [ ] 查看应用日志，是否有连接泄漏警告
- [ ] 检查等待线程数，是否持续大于 0
- [ ] 检查连接使用率，是否接近 100%
- [ ] 启用详细日志，查看连接获取/释放详情
- [ ] 检查是否有长时间运行的事务
- [ ] 检查是否有未正确关闭的数据库连接
- [ ] 考虑增加连接池大小
- [ ] 检查数据库服务器是否有性能问题

