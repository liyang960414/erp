package com.sambound.erp.service.importing;

import cn.idev.excel.context.AnalysisContext;
import cn.idev.excel.read.listener.ReadListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 抽象导入处理器基类
 * 提供通用的批量处理框架、统一的错误收集机制、统一的并发控制、统一的批次处理结果收集
 */
public abstract class AbstractImportProcessor<T> implements ReadListener<T> {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    protected final TransactionTemplate transactionTemplate;
    protected final ExecutorService executorService;
    protected final ImportErrorCollector errorCollector;
    protected final Semaphore batchSemaphore;

    protected final AtomicInteger totalRows = new AtomicInteger(0);

    protected AbstractImportProcessor(TransactionTemplate transactionTemplate,
                                     ExecutorService executorService,
                                     ImportErrorCollector errorCollector) {
        this(transactionTemplate, executorService, errorCollector,
                ImportServiceConfig.DEFAULT_MAX_CONCURRENT_BATCHES);
    }

    protected AbstractImportProcessor(TransactionTemplate transactionTemplate,
                                     ExecutorService executorService,
                                     ImportErrorCollector errorCollector,
                                     int maxConcurrentBatches) {
        this.transactionTemplate = transactionTemplate;
        this.executorService = executorService;
        this.errorCollector = errorCollector;
        this.batchSemaphore = new Semaphore(maxConcurrentBatches);
    }

    @Override
    public void invoke(T data, AnalysisContext context) {
        totalRows.incrementAndGet();
        processRow(data, context);
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext context) {
        logger.info("数据收集完成，共 {} 条数据", totalRows.get());
        onDataCollectionComplete();
    }

    /**
     * 处理单行数据（子类实现）
     */
    protected abstract void processRow(T data, AnalysisContext context);

    /**
     * 数据收集完成后的回调（子类可以覆盖）
     */
    protected void onDataCollectionComplete() {
        // 默认实现为空
    }

    /**
     * 等待所有异步批次处理完成
     *
     * @param futures 批次处理任务列表
     */
    protected void waitForBatches(List<? extends CompletableFuture<?>> futures) {
        if (futures == null || futures.isEmpty()) {
            logger.info("没有需要等待的批次");
            return;
        }

        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(ImportServiceConfig.DEFAULT_BATCH_TIMEOUT_MINUTES, TimeUnit.MINUTES);
        } catch (TimeoutException e) {
            logger.error("导入超时", e);
            futures.forEach(f -> f.cancel(true));
            throw new RuntimeException("导入超时，请检查数据量或重试", e);
        } catch (Exception e) {
            logger.error("批次处理失败", e);
            futures.forEach(f -> f.cancel(true));
            throw new RuntimeException("导入失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取信号量许可（用于并发控制）
     */
    protected void acquireSemaphore() throws InterruptedException {
        batchSemaphore.acquire();
    }

    /**
     * 释放信号量许可
     */
    protected void releaseSemaphore() {
        batchSemaphore.release();
    }

    /**
     * 获取总行数
     */
    public int getTotalRows() {
        return totalRows.get();
    }

    /**
     * 获取错误收集器
     */
    public ImportErrorCollector getErrorCollector() {
        return errorCollector;
    }
}


