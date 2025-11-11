package com.sambound.erp.service.importing;

import java.util.HashMap;
import java.util.Map;

/**
 * 导入上下文，可用于在导入流程中共享数据。
 */
public class ImportContext {

    private final Map<String, Object> attributes = new HashMap<>();

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
}

