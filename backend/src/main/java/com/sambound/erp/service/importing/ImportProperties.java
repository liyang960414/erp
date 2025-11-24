package com.sambound.erp.service.importing;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Excel 导入模块的集中配置，支持默认值与模块级覆盖。
 */
@Component
@ConfigurationProperties(prefix = "erp.import")
public class ImportProperties {

    private final ModuleProperties defaults = new ModuleProperties();
    private Map<String, ModuleProperties> modules = new HashMap<>();

    public ModuleProperties getDefaults() {
        return defaults;
    }

    public Map<String, ModuleProperties> getModules() {
        return modules;
    }

    public void setModules(Map<String, ModuleProperties> modules) {
        this.modules = modules;
    }

    /**
     * 根据模块名称获取合并后的配置。
     */
    public ImportModuleConfig getModuleConfig(String module) {
        String normalized = normalize(module);
        ModuleProperties moduleProperties = modules.getOrDefault(normalized, null);
        ImportModuleConfig base = ImportModuleConfig.defaultConfig(normalized);
        return base.withOverrides(defaults).withOverrides(moduleProperties);
    }

    private String normalize(String module) {
        if (module == null || module.isBlank()) {
            return "default";
        }
        return module.toLowerCase(Locale.ROOT);
    }

    /**
     * yml/json 对应的模块属性。
     */
    public static class ModuleProperties {
        private Integer batchInsertSize;
        private Integer maxConcurrentBatches;
        private Integer batchTimeoutMinutes;
        private Integer maxErrorCount;
        private Integer transactionTimeoutSeconds;
        private ImportModuleConfig.ExecutorType executorType;

        public Integer getBatchInsertSize() {
            return batchInsertSize;
        }

        public void setBatchInsertSize(Integer batchInsertSize) {
            this.batchInsertSize = batchInsertSize;
        }

        public Integer getMaxConcurrentBatches() {
            return maxConcurrentBatches;
        }

        public void setMaxConcurrentBatches(Integer maxConcurrentBatches) {
            this.maxConcurrentBatches = maxConcurrentBatches;
        }

        public Integer getBatchTimeoutMinutes() {
            return batchTimeoutMinutes;
        }

        public void setBatchTimeoutMinutes(Integer batchTimeoutMinutes) {
            this.batchTimeoutMinutes = batchTimeoutMinutes;
        }

        public Integer getMaxErrorCount() {
            return maxErrorCount;
        }

        public void setMaxErrorCount(Integer maxErrorCount) {
            this.maxErrorCount = maxErrorCount;
        }

        public Integer getTransactionTimeoutSeconds() {
            return transactionTimeoutSeconds;
        }

        public void setTransactionTimeoutSeconds(Integer transactionTimeoutSeconds) {
            this.transactionTimeoutSeconds = transactionTimeoutSeconds;
        }

        public ImportModuleConfig.ExecutorType getExecutorType() {
            return executorType;
        }

        public void setExecutorType(ImportModuleConfig.ExecutorType executorType) {
            this.executorType = executorType;
        }
    }
}

