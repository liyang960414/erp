package com.sambound.erp.service.importing.exception;

/**
 * 导入处理过程异常
 */
public class ImportProcessingException extends ImportException {
    public ImportProcessingException(String message) {
        super(message);
    }

    public ImportProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
