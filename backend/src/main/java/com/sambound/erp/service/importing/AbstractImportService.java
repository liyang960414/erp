package com.sambound.erp.service.importing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Excel 导入服务抽象基类
 * 统一 ImportService 的公共逻辑（ExecutorService、TransactionTemplate、文件读取）
 */
public abstract class AbstractImportService<R> {

    protected final Logger logger = LoggerFactory.getLogger(getClass());
    protected final TransactionTemplate transactionTemplate;
    protected final ExecutorService executorService;

    protected AbstractImportService(PlatformTransactionManager transactionManager) {
        this(transactionManager, ImportServiceConfig.DEFAULT_TRANSACTION_TIMEOUT);
    }

    protected AbstractImportService(PlatformTransactionManager transactionManager, int transactionTimeout) {
        this.transactionTemplate = createTransactionTemplate(transactionManager, transactionTimeout);
        this.executorService = Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * 创建事务模板
     */
    protected TransactionTemplate createTransactionTemplate(PlatformTransactionManager transactionManager, int timeout) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
        template.setTimeout(timeout);
        return template;
    }

    /**
     * 从上传的 Excel 文件执行导入
     * 使用 InputStream 而非 byte[]，避免一次性加载大文件到内存
     *
     * @param file 上传的 Excel 文件
     * @return 导入结果
     */
    public R importFromExcel(MultipartFile file) {
        try {
            String fileName = file.getOriginalFilename();
            long fileSize = file.getSize();
            logger.info("开始导入Excel文件: {}，文件大小: {} MB", fileName, fileSize / (1024.0 * 1024.0));
            
            try (InputStream inputStream = file.getInputStream()) {
                R result = importFromInputStream(inputStream, fileName, fileSize);
                logImportResult(result);
                return result;
            }
        } catch (Exception e) {
            logger.error("Excel文件导入失败: {}", file.getOriginalFilename(), e);
            throw new RuntimeException("Excel文件导入失败: " + e.getMessage(), e);
        }
    }

    /**
     * 从字节数组执行导入（兼容旧代码）
     *
     * @param fileBytes 文件字节数组
     * @param fileName  文件名
     * @return 导入结果
     */
    public R importFromBytes(byte[] fileBytes, String fileName) {
        return importFromBytes(fileBytes, fileName, fileBytes.length);
    }

    /**
     * 从字节数组执行导入（兼容旧代码）
     *
     * @param fileBytes 文件字节数组
     * @param fileName  文件名
     * @param fileSize  文件大小
     * @return 导入结果
     */
    public R importFromBytes(byte[] fileBytes, String fileName, long fileSize) {
        try {
            logger.info("开始导入Excel文件: {}，文件大小: {} MB", fileName, fileSize / (1024.0 * 1024.0));
            
            try (InputStream inputStream = new java.io.ByteArrayInputStream(fileBytes)) {
                R result = importFromInputStream(inputStream, fileName, fileSize);
                logImportResult(result);
                return result;
            }
        } catch (Exception e) {
            logger.error("Excel文件导入失败: {}", fileName, e);
            throw new RuntimeException("Excel文件导入失败: " + e.getMessage(), e);
        }
    }

    /**
     * 从输入流执行导入（核心方法）
     *
     * @param inputStream 输入流
     * @param fileName    文件名
     * @param fileSize    文件大小
     * @return 导入结果
     */
    protected abstract R importFromInputStream(InputStream inputStream, String fileName, long fileSize) throws Exception;

    /**
     * 记录导入结果（子类可以覆盖以提供更详细的日志）
     */
    protected void logImportResult(R result) {
        // 默认实现为空，子类可以覆盖
    }
}


