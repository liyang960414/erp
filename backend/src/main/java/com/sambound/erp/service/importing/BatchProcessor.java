package com.sambound.erp.service.importing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Function;

/**
 * 通用批量处理工具
 * 支持分批处理、并发控制、统一的批次结果收集、超时处理
 */
public class BatchProcessor {

    private static final Logger logger = LoggerFactory.getLogger(BatchProcessor.class);

    private final int batchSize;
    private final int maxConcurrentBatches;
    private final int timeoutMinutes;
    private final ExecutorService executorService;
    private final Semaphore batchSemaphore;

    public BatchProcessor(int batchSize, int maxConcurrentBatches, int timeoutMinutes, ExecutorService executorService) {
        this.batchSize = batchSize;
        this.maxConcurrentBatches = maxConcurrentBatches;
        this.timeoutMinutes = timeoutMinutes;
        this.executorService = executorService;
        this.batchSemaphore = new Semaphore(maxConcurrentBatches);
    }

    public BatchProcessor(ExecutorService executorService) {
        this(ImportServiceConfig.DEFAULT_BATCH_INSERT_SIZE,
                ImportServiceConfig.DEFAULT_MAX_CONCURRENT_BATCHES,
                ImportServiceConfig.DEFAULT_BATCH_TIMEOUT_MINUTES,
                executorService);
    }

    /**
     * 批量处理数据
     *
     * @param dataList     数据列表
     * @param batchHandler 批次处理函数
     * @param <T>          数据类型
     * @param <R>          结果类型
     * @return 处理结果
     */
    public <T, R> BatchResult<R> processBatches(List<T> dataList, Function<List<T>, R> batchHandler) {
        if (dataList == null || dataList.isEmpty()) {
            logger.info("没有需要处理的数据");
            return new BatchResult<>(0, new ArrayList<>(), new ArrayList<>());
        }

        int totalBatches = (dataList.size() + batchSize - 1) / batchSize;
        logger.info("开始批量处理 {} 条数据，共 {} 个批次", dataList.size(), totalBatches);

        List<CompletableFuture<BatchItemResult<R>>> futures = new ArrayList<>();

        for (int i = 0; i < dataList.size(); i += batchSize) {
            int end = Math.min(i + batchSize, dataList.size());
            List<T> batch = new ArrayList<>(dataList.subList(i, end));
            int batchIndex = (i / batchSize) + 1;

            CompletableFuture<BatchItemResult<R>> future = CompletableFuture.supplyAsync(() -> {
                try {
                    batchSemaphore.acquire();
                    try {
                        long batchStartTime = System.currentTimeMillis();
                        logger.debug("处理批次 {}/{}，数据量: {}", batchIndex, totalBatches, batch.size());

                        R result = batchHandler.apply(batch);

                        long batchDuration = System.currentTimeMillis() - batchStartTime;
                        logger.debug("批次 {}/{} 完成，耗时: {}ms", batchIndex, totalBatches, batchDuration);

                        return new BatchItemResult<>(batchIndex, result, null);
                    } finally {
                        batchSemaphore.release();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.warn("批次 {} 处理被中断", batchIndex);
                    return new BatchItemResult<>(batchIndex, null, new RuntimeException("批次处理被中断", e));
                } catch (Exception e) {
                    logger.error("批次 {} 处理失败", batchIndex, e);
                    return new BatchItemResult<>(batchIndex, null, e);
                }
            }, executorService);

            futures.add(future);
        }

        return waitForBatches(futures, totalBatches);
    }

    /**
     * 等待所有批次完成
     */
    private <R> BatchResult<R> waitForBatches(List<CompletableFuture<BatchItemResult<R>>> futures, int totalBatches) {
        List<R> results = new ArrayList<>();
        List<Exception> errors = new ArrayList<>();

        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(timeoutMinutes, TimeUnit.MINUTES);

            for (CompletableFuture<BatchItemResult<R>> future : futures) {
                try {
                    BatchItemResult<R> itemResult = future.get();
                    if (itemResult.result() != null) {
                        results.add(itemResult.result());
                    }
                    if (itemResult.error() != null) {
                        errors.add(itemResult.error());
                    }
                } catch (Exception e) {
                    logger.error("获取批次结果失败", e);
                    errors.add(e);
                }
            }
        } catch (TimeoutException e) {
            logger.error("批量处理超时", e);
            futures.forEach(f -> f.cancel(true));
            throw new RuntimeException("批量处理超时，请检查数据量或重试", e);
        } catch (Exception e) {
            logger.error("批量处理失败", e);
            futures.forEach(f -> f.cancel(true));
            throw new RuntimeException("批量处理失败: " + e.getMessage(), e);
        }

        logger.info("批量处理完成：共 {} 个批次，成功 {} 个，失败 {} 个",
                totalBatches, results.size(), errors.size());

        return new BatchResult<>(totalBatches, results, errors);
    }

    /**
     * 批次处理结果
     */
    public static class BatchResult<R> {
        private final int totalBatches;
        private final List<R> results;
        private final List<Exception> errors;

        public BatchResult(int totalBatches, List<R> results, List<Exception> errors) {
            this.totalBatches = totalBatches;
            this.results = results;
            this.errors = errors;
        }

        public int getTotalBatches() {
            return totalBatches;
        }

        public List<R> getResults() {
            return results;
        }

        public List<Exception> getErrors() {
            return errors;
        }

        public boolean hasErrors() {
            return !errors.isEmpty();
        }
    }

    /**
     * 批次项结果
     */
    private record BatchItemResult<R>(int batchIndex, R result, Exception error) {
    }
}



