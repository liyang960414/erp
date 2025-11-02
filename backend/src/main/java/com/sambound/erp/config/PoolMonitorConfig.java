package com.sambound.erp.config;

import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * 连接池监控配置
 * 定期记录连接池状态，并在异常时发出警告
 */
@Configuration
@EnableScheduling
public class PoolMonitorConfig {

    /**
     * 定期记录连接池状态
     */
    @Component
    public static class PoolStatusLogger {
        
        private static final Logger logger = LoggerFactory.getLogger(PoolStatusLogger.class);
        private final HikariDataSource dataSource;

        @Autowired
        public PoolStatusLogger(DataSource dataSource) {
            if (dataSource instanceof HikariDataSource) {
                this.dataSource = (HikariDataSource) dataSource;
            } else {
                throw new IllegalStateException("DataSource 不是 HikariDataSource 实例");
            }
        }

        /**
         * 每5分钟记录一次连接池状态
         */
        @Scheduled(fixedRate = 300000) // 5分钟
        public void logPoolStatus() {
            try {
                var poolMXBean = dataSource.getHikariPoolMXBean();
                if (poolMXBean != null) {
                    int active = poolMXBean.getActiveConnections();
                    int idle = poolMXBean.getIdleConnections();
                    int total = poolMXBean.getTotalConnections();
                    int max = dataSource.getMaximumPoolSize();
                    int waiting = poolMXBean.getThreadsAwaitingConnection();
                    double usageRate = max > 0 ? (double) active / max * 100 : 0;

                    logger.info("连接池状态 - 活跃: {}/{}, 空闲: {}, 总数: {}, 等待线程: {}, 使用率: {:.2f}%",
                            active, max, idle, total, waiting, usageRate);

                    // 如果使用率过高或有问题，记录警告
                    if (usageRate >= 90) {
                        logger.warn("连接池使用率过高: {:.2f}%, 可能导致连接获取超时", usageRate);
                    }
                    if (waiting > 0) {
                        logger.warn("有 {} 个线程正在等待获取连接，可能存在连接泄漏或连接池配置不足", waiting);
                    }
                    if (total > max) {
                        logger.error("连接总数超过最大池大小！总数: {}, 最大: {}", total, max);
                    }
                }
            } catch (Exception e) {
                logger.error("记录连接池状态失败", e);
            }
        }
    }
}

