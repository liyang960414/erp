package com.sambound.erp.service.importing.exception;

/**
 * 导入系统基础异常类
 */
public class ImportException extends RuntimeException {
    public ImportException(String message) {
        super(message);
    }

    public ImportException(String message, Throwable cause) {
        super(message, cause);
    }
}
