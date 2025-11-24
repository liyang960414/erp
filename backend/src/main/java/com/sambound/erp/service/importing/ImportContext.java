package com.sambound.erp.service.importing;

import java.util.HashMap;
import java.util.Map;

/**
 * 导入上下文，可用于在导入流程中共享数据。
 */
public class ImportContext {

    private final Map<String, Object> attributes = new HashMap<>();
    private String module;
    private String fileName;
    private long fileSizeBytes;
    private ImportModuleConfig moduleConfig;
    private long startTimeMillis;

    public void setAttribute(String key, Object value) {
        attributes.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key) {
        return (T) attributes.get(key);
    }

    public boolean containsAttribute(String key) {
        return attributes.containsKey(key);
    }

    public Object removeAttribute(String key) {
        return attributes.remove(key);
    }

    public String getModule() {
        return module;
    }

    public ImportContext setModule(String module) {
        this.module = module;
        return this;
    }

    public String getFileName() {
        return fileName;
    }

    public ImportContext setFileName(String fileName) {
        this.fileName = fileName;
        return this;
    }

    public long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public ImportContext setFileSizeBytes(long fileSizeBytes) {
        this.fileSizeBytes = fileSizeBytes;
        return this;
    }

    public ImportModuleConfig getModuleConfig() {
        return moduleConfig;
    }

    public ImportContext setModuleConfig(ImportModuleConfig moduleConfig) {
        this.moduleConfig = moduleConfig;
        return this;
    }

    public long getStartTimeMillis() {
        return startTimeMillis;
    }

    public ImportContext setStartTimeMillis(long startTimeMillis) {
        this.startTimeMillis = startTimeMillis;
        return this;
    }
}

