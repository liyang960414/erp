package com.sambound.erp.service.importing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
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
    protected final ImportModuleConfig moduleConfig;
    private final ThreadLocal<ImportContext> currentContext = new ThreadLocal<>();

    protected AbstractImportService(PlatformTransactionManager transactionManager) {
        this(transactionManager, ImportModuleConfig.defaultConfig("default"));
    }

    protected AbstractImportService(PlatformTransactionManager transactionManager, int transactionTimeout) {
        this(transactionManager, ImportModuleConfig.builder()
                .module("default")
                .transactionTimeoutSeconds(transactionTimeout)
                .build());
    }

    protected AbstractImportService(PlatformTransactionManager transactionManager, ImportModuleConfig moduleConfig) {
        this.moduleConfig = moduleConfig == null
                ? ImportModuleConfig.defaultConfig("default")
                : moduleConfig;
        this.transactionTemplate = createTransactionTemplate(
                transactionManager,
                this.moduleConfig.transactionTimeoutSeconds());
        this.executorService = createExecutorService(this.moduleConfig);
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
            ImportContext context = createContext(fileName, fileSize);
            currentContext.set(context);
            logger.info("[ExcelImport] stage=start module={} file={} sizeMB={}",
                    context.getModule(), fileName, toMb(fileSize));
            
            Path tempFile = writeToTempFile(file.getInputStream(), fileName);
            try (InputStream inputStream = Files.newInputStream(tempFile)) {
                R result = importFromInputStream(inputStream, fileName, fileSize, tempFile);
                logImportResult(result, context);
                return result;
            } finally {
                deleteTempFile(tempFile);
            }
        } catch (Exception e) {
            logger.error("[ExcelImport] stage=failed module={} file={}",
                    moduleConfig.module(), file.getOriginalFilename(), e);
            throw new RuntimeException("Excel文件导入失败: " + e.getMessage(), e);
        } finally {
            currentContext.remove();
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
            ImportContext context = createContext(fileName, fileSize);
            currentContext.set(context);
            logger.info("[ExcelImport] stage=start module={} file={} sizeMB={}",
                    context.getModule(), fileName, toMb(fileSize));
            
            Path tempFile = writeToTempFile(new java.io.ByteArrayInputStream(fileBytes), fileName);
            try (InputStream inputStream = Files.newInputStream(tempFile)) {
                R result = importFromInputStream(inputStream, fileName, fileSize, tempFile);
                logImportResult(result, context);
                return result;
            } finally {
                deleteTempFile(tempFile);
            }
        } catch (Exception e) {
            logger.error("[ExcelImport] stage=failed module={} file={}",
                    moduleConfig.module(), fileName, e);
            throw new RuntimeException("Excel文件导入失败: " + e.getMessage(), e);
        } finally {
            currentContext.remove();
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
     * 从输入流执行导入（支持多 sheet 读取）
     * 如果 Processor 需要读取多个 sheet，可以使用此方法，临时文件路径会保存在 context 中
     *
     * @param inputStream 输入流
     * @param fileName    文件名
     * @param fileSize    文件大小
     * @param tempFile    临时文件路径
     * @return 导入结果
     */
    protected R importFromInputStream(InputStream inputStream, String fileName, long fileSize, Path tempFile) throws Exception {
        // 将临时文件路径保存到 context，供子类使用
        ImportContext context = getCurrentContext();
        if (context != null) {
            context.setAttribute("tempFile", tempFile);
        }
        return importFromInputStream(inputStream, fileName, fileSize);
    }

    /**
     * 记录导入结果（子类可以覆盖以提供更详细的日志）
     */
    protected void logImportResult(R result) {
        // 默认实现为空，子类可以覆盖
    }

    protected void logImportResult(R result, ImportContext context) {
        logImportResult(result);
    }

    protected ImportContext getCurrentContext() {
        return currentContext.get();
    }

    protected ImportContext createContext(String fileName, long fileSize) {
        return new ImportContext()
                .setModule(moduleConfig.module())
                .setFileName(fileName)
                .setFileSizeBytes(fileSize)
                .setModuleConfig(moduleConfig)
                .setStartTimeMillis(System.currentTimeMillis());
    }

    private ExecutorService createExecutorService(ImportModuleConfig config) {
        return switch (config.executorType()) {
            case VIRTUAL_THREAD -> Executors.newVirtualThreadPerTaskExecutor();
            case FIXED_THREAD_POOL -> Executors.newFixedThreadPool(config.maxConcurrentBatches());
        };
    }

    private double toMb(long bytes) {
        return Math.round((bytes / (1024.0 * 1024.0)) * 100.0) / 100.0;
    }

    private Path writeToTempFile(InputStream inputStream, String originalName) throws java.io.IOException {
        Path tempFile = createTempFile(originalName);
        try (InputStream in = inputStream; OutputStream outputStream = Files.newOutputStream(tempFile)) {
            in.transferTo(outputStream);
        }
        return tempFile;
    }

    private Path createTempFile(String originalName) throws java.io.IOException {
        String suffix = originalName != null && originalName.contains(".")
                ? originalName.substring(originalName.lastIndexOf('.'))
                : ".tmp";
        return Files.createTempFile("excel-import-", suffix);
    }

    private void deleteTempFile(Path tempFile) {
        if (tempFile == null) {
            return;
        }
        try {
            Files.deleteIfExists(tempFile);
        } catch (Exception ex) {
            logger.warn("[ExcelImport] 无法删除临时文件: {}", tempFile, ex);
        }
    }
}



