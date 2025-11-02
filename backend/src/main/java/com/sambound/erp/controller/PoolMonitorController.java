package com.sambound.erp.controller;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * HikariCP 连接池监控端点
 * 提供连接池的实时状态信息
 */
@RestController
@RequestMapping("/api/monitor/pool")
@PreAuthorize("hasRole('ADMIN')")
public class PoolMonitorController {

    private static final Logger logger = LoggerFactory.getLogger(PoolMonitorController.class);

    private final HikariDataSource dataSource;

    @Autowired
    public PoolMonitorController(DataSource dataSource) {
        if (dataSource instanceof HikariDataSource) {
            this.dataSource = (HikariDataSource) dataSource;
        } else {
            throw new IllegalStateException("DataSource 不是 HikariDataSource 实例");
        }
    }

    /**
     * 获取连接池的详细状态信息
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getPoolStatus() {
        try {
            HikariPoolMXBean poolMXBean = dataSource.getHikariPoolMXBean();
            
            Map<String, Object> status = new HashMap<>();
            
            // 基本配置信息
            Map<String, Object> config = new HashMap<>();
            config.put("maximumPoolSize", dataSource.getMaximumPoolSize());
            config.put("minimumIdle", dataSource.getMinimumIdle());
            config.put("connectionTimeout", dataSource.getConnectionTimeout());
            config.put("idleTimeout", dataSource.getIdleTimeout());
            config.put("maxLifetime", dataSource.getMaxLifetime());
            config.put("leakDetectionThreshold", dataSource.getLeakDetectionThreshold());
            status.put("config", config);
            
            // 运行时状态信息
            Map<String, Object> runtime = new HashMap<>();
            runtime.put("activeConnections", poolMXBean != null ? poolMXBean.getActiveConnections() : -1);
            runtime.put("idleConnections", poolMXBean != null ? poolMXBean.getIdleConnections() : -1);
            runtime.put("totalConnections", poolMXBean != null ? poolMXBean.getTotalConnections() : -1);
            runtime.put("threadsAwaitingConnection", poolMXBean != null ? poolMXBean.getThreadsAwaitingConnection() : -1);
            status.put("runtime", runtime);
            
            // 计算使用率和健康状态
            int maxPoolSize = dataSource.getMaximumPoolSize();
            int activeConnections = poolMXBean != null ? poolMXBean.getActiveConnections() : 0;
            int totalConnections = poolMXBean != null ? poolMXBean.getTotalConnections() : 0;
            
            Map<String, Object> health = new HashMap<>();
            double usageRate = maxPoolSize > 0 ? (double) activeConnections / maxPoolSize * 100 : 0;
            health.put("usageRate", String.format("%.2f%%", usageRate));
            health.put("availableConnections", maxPoolSize - activeConnections);
            health.put("connectionUtilization", String.format("%d/%d", activeConnections, maxPoolSize));
            
            // 健康状态判断
            String healthStatus;
            if (usageRate >= 90) {
                healthStatus = "CRITICAL";
            } else if (usageRate >= 75) {
                healthStatus = "WARNING";
            } else {
                healthStatus = "HEALTHY";
            }
            health.put("status", healthStatus);
            status.put("health", health);
            
            return ResponseEntity.ok(status);
        } catch (Exception e) {
            logger.error("获取连接池状态失败", e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "获取连接池状态失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * 获取连接池的简要信息（用于快速检查）
     */
    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getPoolSummary() {
        try {
            HikariPoolMXBean poolMXBean = dataSource.getHikariPoolMXBean();
            
            Map<String, Object> summary = new HashMap<>();
            
            if (poolMXBean != null) {
                int active = poolMXBean.getActiveConnections();
                int idle = poolMXBean.getIdleConnections();
                int total = poolMXBean.getTotalConnections();
                int max = dataSource.getMaximumPoolSize();
                int waiting = poolMXBean.getThreadsAwaitingConnection();
                
                summary.put("activeConnections", active);
                summary.put("idleConnections", idle);
                summary.put("totalConnections", total);
                summary.put("maxPoolSize", max);
                summary.put("availableConnections", max - active);
                summary.put("waitingThreads", waiting);
                summary.put("usageRate", String.format("%.1f%%", (double) active / max * 100));
            } else {
                summary.put("error", "无法获取连接池状态");
            }
            
            return ResponseEntity.ok(summary);
        } catch (Exception e) {
            logger.error("获取连接池摘要失败", e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "获取连接池摘要失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * 强制关闭空闲连接（紧急情况使用）
     * 注意：仅在连接池出现问题时使用
     */
    @GetMapping("/evict-idle")
    public ResponseEntity<Map<String, String>> evictIdleConnections() {
        try {
            HikariPoolMXBean poolMXBean = dataSource.getHikariPoolMXBean();
            if (poolMXBean != null) {
                poolMXBean.softEvictConnections();
                Map<String, String> result = new HashMap<>();
                result.put("message", "已触发空闲连接清理");
                result.put("status", "success");
                logger.info("手动触发空闲连接清理");
                return ResponseEntity.ok(result);
            } else {
                Map<String, String> error = new HashMap<>();
                error.put("error", "无法访问连接池");
                return ResponseEntity.badRequest().body(error);
            }
        } catch (Exception e) {
            logger.error("清理空闲连接失败", e);
            Map<String, String> error = new HashMap<>();
            error.put("error", "清理失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }
}

