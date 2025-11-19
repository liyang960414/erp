package com.sambound.erp.service.importing.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sambound.erp.importing.task.ImportDependencyProperties;
import com.sambound.erp.importing.task.ImportFailureStatus;
import com.sambound.erp.importing.task.ImportTask;
import com.sambound.erp.importing.task.ImportTaskDependency;
import com.sambound.erp.importing.task.ImportTaskFailure;
import com.sambound.erp.importing.task.ImportTaskItem;
import com.sambound.erp.importing.task.ImportTaskItemStatus;
import com.sambound.erp.importing.task.ImportTaskStatus;
import com.sambound.erp.repository.ImportTaskDependencyRepository;
import com.sambound.erp.repository.ImportTaskFailureRepository;
import com.sambound.erp.repository.ImportTaskItemRepository;
import com.sambound.erp.repository.ImportTaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 导入任务调度器，负责检测依赖并将任务提交到后台线程执行。
 */
@Service
public class ImportTaskScheduler {

    private static final Logger logger = LoggerFactory.getLogger(ImportTaskScheduler.class);

    private final ImportTaskRepository taskRepository;
    private final ImportTaskItemRepository taskItemRepository;
    private final ImportTaskDependencyRepository dependencyRepository;
    private final ImportTaskFailureRepository failureRepository;
    private final ImportDependencyProperties properties;
    private final Map<String, ImportTaskHandler> handlerMap;
    private final Executor executor;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;

    private final Set<Long> runningTasks = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean schedulerRunning = new AtomicBoolean(false);

    public ImportTaskScheduler(
            ImportTaskRepository taskRepository,
            ImportTaskItemRepository taskItemRepository,
            ImportTaskDependencyRepository dependencyRepository,
            ImportTaskFailureRepository failureRepository,
            ImportDependencyProperties properties,
            List<ImportTaskHandler> handlers,
            @Qualifier("importTaskExecutor") Executor executor,
            PlatformTransactionManager transactionManager,
            ObjectProvider<ObjectMapper> objectMapperProvider) {
        this.taskRepository = taskRepository;
        this.taskItemRepository = taskItemRepository;
        this.dependencyRepository = dependencyRepository;
        this.failureRepository = failureRepository;
        this.properties = properties;
        this.executor = executor;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
        this.transactionTemplate.setTimeout(300);
        this.objectMapper = Optional.ofNullable(objectMapperProvider.getIfAvailable()).orElseGet(ObjectMapper::new);
        this.handlerMap = new HashMap<>();
        for (ImportTaskHandler handler : handlers) {
            this.handlerMap.put(handler.getImportType(), handler);
        }
    }

    /**
     * 周期性执行调度流程。
     */
    @Scheduled(fixedDelayString = "${erp.import.scheduler.poll-interval:2000}")
    public void schedule() {
        if (!schedulerRunning.compareAndSet(false, true)) {
            return;
        }
        try {
            processWaitingTasks();
            launchQueuedTasks();
        } catch (Exception ex) {
            logger.error("调度导入任务失败", ex);
        } finally {
            schedulerRunning.set(false);
        }
    }

    private void processWaitingTasks() {
        List<ImportTask> waitingTasks = taskRepository.findTop50ByStatusInOrderByCreatedAtAsc(
                List.of(ImportTaskStatus.WAITING));
        if (waitingTasks.isEmpty()) {
            return;
        }
        for (ImportTask task : waitingTasks) {
            promoteIfDependenciesSatisfied(task.getId());
        }
    }

    private void promoteIfDependenciesSatisfied(Long taskId) {
        transactionTemplate.executeWithoutResult(status -> {
            ImportTask task = taskRepository.findByIdForUpdate(taskId).orElse(null);
            if (task == null || task.getStatus() != ImportTaskStatus.WAITING) {
                return;
            }
            List<ImportTaskDependency> dependencies = dependencyRepository.findByTaskId(task.getId());
            if (dependencies.isEmpty()) {
                task.setStatus(ImportTaskStatus.QUEUED);
                task.setScheduledAt(LocalDateTime.now());
                logger.debug("任务 {} 无依赖，进入排队状态", task.getTaskCode());
                return;
            }
            boolean allCompleted = true;
            for (ImportTaskDependency dependency : dependencies) {
                ImportTask dependsOn = dependency.getDependsOn();
                ImportTaskStatus dependsOnStatus = dependsOn.getStatus();
                if (dependsOnStatus == ImportTaskStatus.FAILED || dependsOnStatus == ImportTaskStatus.CANCELLED) {
                    task.setStatus(ImportTaskStatus.FAILED);
                    task.setFailureReason("前置任务失败: " + dependsOn.getTaskCode());
                    task.setCompletedAt(LocalDateTime.now());
                    logger.warn("任务 {} 因前置任务 {} 失败而终止", task.getTaskCode(), dependsOn.getTaskCode());
                    return;
                }
                if (dependsOnStatus != ImportTaskStatus.COMPLETED) {
                    allCompleted = false;
                }
            }
            if (allCompleted) {
                task.setStatus(ImportTaskStatus.QUEUED);
                task.setScheduledAt(LocalDateTime.now());
                logger.info("任务 {} 所有依赖完成，进入排队状态", task.getTaskCode());
            }
        });
    }

    private void launchQueuedTasks() {
        List<ImportTask> queuedTasks = taskRepository.findTop50ByStatusInOrderByCreatedAtAsc(
                List.of(ImportTaskStatus.QUEUED));
        if (queuedTasks.isEmpty()) {
            return;
        }

        int maxGlobal = properties.getScheduler().getMaxConcurrentTasks();
        long currentRunning = taskRepository.countByStatus(ImportTaskStatus.RUNNING);
        if (currentRunning >= maxGlobal) {
            return;
        }

        Map<String, Long> perTypeRunningCache = new HashMap<>();

        for (ImportTask task : queuedTasks) {
            if (runningTasks.contains(task.getId())) {
                continue;
            }
            if (currentRunning >= maxGlobal) {
                break;
            }
            String importType = task.getImportType();
            long typeRunning = perTypeRunningCache.computeIfAbsent(importType,
                    key -> taskRepository.countByImportTypeAndStatus(key, ImportTaskStatus.RUNNING));
            int typeLimit = properties.resolveConcurrencyLimit(importType);
            if (typeRunning >= typeLimit) {
                continue;
            }
            Optional<Long> nextItemId = findNextPendingItemId(task.getId());
            if (nextItemId.isEmpty()) {
                completeTaskIfNoItems(task.getId());
                continue;
            }
            if (!handlerMap.containsKey(importType)) {
                failTaskWithoutHandler(task.getId(), "缺少导入处理器: " + importType);
                continue;
            }
            if (runningTasks.add(task.getId())) {
                currentRunning++;
                perTypeRunningCache.put(importType, typeRunning + 1);
                submitTask(task.getId(), nextItemId.get());
            }
        }
    }

    private Optional<Long> findNextPendingItemId(Long taskId) {
        return taskItemRepository.findByTaskIdOrderBySequenceNo(taskId).stream()
                .filter(item -> item.getStatus() == ImportTaskItemStatus.PENDING)
                .min(Comparator.comparingInt(ImportTaskItem::getSequenceNo))
                .map(ImportTaskItem::getId);
    }

    private void completeTaskIfNoItems(Long taskId) {
        transactionTemplate.executeWithoutResult(status -> {
            ImportTask task = taskRepository.findByIdForUpdate(taskId).orElse(null);
            if (task == null) {
                return;
            }
            boolean stillPending = task.getItems().stream()
                    .anyMatch(item -> item.getStatus() == ImportTaskItemStatus.PENDING
                            || item.getStatus() == ImportTaskItemStatus.RUNNING);
            if (!stillPending && task.getStatus() != ImportTaskStatus.COMPLETED) {
                task.setStatus(ImportTaskStatus.COMPLETED);
                task.setCompletedAt(LocalDateTime.now());
                logger.info("任务 {} 无待执行子项，直接标记为完成", task.getTaskCode());
            }
        });
    }

    private void failTaskWithoutHandler(Long taskId, String reason) {
        transactionTemplate.executeWithoutResult(status -> {
            ImportTask task = taskRepository.findByIdForUpdate(taskId).orElse(null);
            if (task == null) {
                return;
            }
            task.setStatus(ImportTaskStatus.FAILED);
            task.setFailureReason(reason);
            task.setCompletedAt(LocalDateTime.now());
        });
    }

    private void submitTask(Long taskId, Long itemId) {
        CompletableFuture.runAsync(() -> executeTask(taskId, itemId), executor)
                .whenComplete((unused, throwable) -> runningTasks.remove(taskId));
    }

    private void executeTask(Long taskId, Long itemId) {
        TaskExecutionContext context = prepareExecution(taskId, itemId);
        if (context == null) {
            return;
        }
        ImportTaskExecutionResult result = null;
        Exception executionException = null;
        try {
            ImportTaskHandler handler = handlerMap.get(context.importType());
            if (handler == null) {
                throw new IllegalStateException("未找到导入处理器: " + context.importType());
            }
            ImportTaskContext handlerContext = new ImportTaskContext(
                    context.taskCode(),
                    context.importType(),
                    context.fileContent(),
                    context.fileName(),
                    context.contentType(),
                    context.optionsJson());
            result = handler.execute(handlerContext);
        } catch (Exception ex) {
            executionException = ex;
            logger.error("执行导入任务失败: taskId={}, itemId={}", taskId, itemId, ex);
        }
        finalizeExecution(context, result, executionException);
    }

    private TaskExecutionContext prepareExecution(Long taskId, Long itemId) {
        return transactionTemplate.execute(status -> {
            ImportTask task = taskRepository.findByIdForUpdate(taskId).orElse(null);
            if (task == null) {
                return null;
            }
            if (task.getStatus() != ImportTaskStatus.QUEUED && task.getStatus() != ImportTaskStatus.RUNNING) {
                return null;
            }
            ImportTaskItem item = taskItemRepository.findById(itemId).orElse(null);
            if (item == null) {
                return null;
            }
            if (item.getStatus() != ImportTaskItemStatus.PENDING) {
                return null;
            }
            LocalDateTime now = LocalDateTime.now();
            task.setStatus(ImportTaskStatus.RUNNING);
            if (task.getStartedAt() == null) {
                task.setStartedAt(now);
            }
            item.setStatus(ImportTaskItemStatus.RUNNING);
            item.setScheduledAt(now);
            item.setStartedAt(now);
            byte[] fileContent = item.getFileContent();
            return new TaskExecutionContext(
                    task.getId(),
                    task.getTaskCode(),
                    task.getImportType(),
                    item.getId(),
                    item.getSourceFileName(),
                    item.getContentType(),
                    task.getOptionsJson(),
                    fileContent != null ? fileContent : new byte[0]
            );
        });
    }

    private void finalizeExecution(TaskExecutionContext context,
                                   ImportTaskExecutionResult result,
                                   Exception exception) {
        transactionTemplate.executeWithoutResult(status -> {
            ImportTask task = taskRepository.findByIdForUpdate(context.taskId()).orElse(null);
            ImportTaskItem item = taskItemRepository.findById(context.itemId()).orElse(null);
            if (task == null || item == null) {
                return;
            }
            LocalDateTime now = LocalDateTime.now();
            if (exception == null && result != null) {
                item.setStatus(ImportTaskItemStatus.COMPLETED);
                item.setCompletedAt(now);
                item.setTotalCount(result.getTotalCount());
                item.setSuccessCount(result.getSuccessCount());
                item.setFailureCount(result.getFailureCount());
                item.setPayloadJson(mergePayload(item.getPayloadJson(), result.getSummary()));
                task.setTotalCount(result.getTotalCount());
                task.setSuccessCount(result.getSuccessCount());
                task.setFailureCount(result.getFailureCount());
                if (result.getFailureCount() > 0 && !result.getFailures().isEmpty()) {
                    persistFailures(task, item, result.getFailures());
                }
                resolveResubmittedFailures(task, item, now);
                refreshTaskStatistics(task);
                if (!hasPendingItems(task)) {
                    task.setStatus(ImportTaskStatus.COMPLETED);
                    task.setCompletedAt(now);
                    logger.info("任务 {} 执行完成，总数 {}，成功 {}，失败 {}",
                            task.getTaskCode(), result.getTotalCount(),
                            result.getSuccessCount(), result.getFailureCount());
                } else {
                    task.setStatus(ImportTaskStatus.QUEUED);
                    task.setScheduledAt(now);
                }
            } else {
                item.setStatus(ImportTaskItemStatus.FAILED);
                item.setFailureReason(exception != null ? exception.getMessage() : "任务返回空结果");
                item.setCompletedAt(now);
                task.setStatus(ImportTaskStatus.FAILED);
                task.setFailureReason(item.getFailureReason());
                task.setCompletedAt(now);
            }
        });
    }

    private void persistFailures(ImportTask task,
                                 ImportTaskItem item,
                                 List<ImportTaskFailureDetail> failures) {
        List<ImportTaskFailure> entities = new ArrayList<>(failures.size());
        for (ImportTaskFailureDetail detail : failures) {
            ImportTaskFailure failure = ImportTaskFailure.builder()
                    .task(task)
                    .taskItem(item)
                    .section(detail.error().getSection())
                    .rowNumber(detail.error().getRowNumber())
                    .field(detail.error().getField())
                    .message(detail.error().getMessage())
                    .rawPayload(detail.rawPayload())
                    .build();
            entities.add(failure);
        }
        failureRepository.saveAll(entities);
        task.getFailures().addAll(entities);
        item.getFailures().addAll(entities);
    }

    private boolean hasPendingItems(ImportTask task) {
        Collection<ImportTaskItem> items = task.getItems();
        return items.stream().anyMatch(item ->
                item.getStatus() == ImportTaskItemStatus.PENDING
                        || item.getStatus() == ImportTaskItemStatus.RUNNING);
    }

    private String mergePayload(String existingPayload, Object summary) {
        if (summary == null) {
            return existingPayload;
        }
        try {
            com.fasterxml.jackson.databind.node.ObjectNode root;
            if (existingPayload != null && !existingPayload.isBlank()) {
                com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(existingPayload);
                if (node instanceof com.fasterxml.jackson.databind.node.ObjectNode objectNode) {
                    root = objectNode;
                } else {
                    root = objectMapper.createObjectNode();
                    root.set("previous", node);
                }
            } else {
                root = objectMapper.createObjectNode();
            }
            root.set("summary", objectMapper.valueToTree(summary));
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            logger.warn("任务结果序列化失败: {}", e.getMessage());
            return summary.toString();
        }
    }

    private void resolveResubmittedFailures(ImportTask task, ImportTaskItem item, LocalDateTime now) {
        String payload = item.getPayloadJson();
        if (payload == null || payload.isBlank()) {
            return;
        }
        try {
            com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(payload);
            com.fasterxml.jackson.databind.JsonNode idsNode = node.get("retryFailureIds");
            if (idsNode == null || !idsNode.isArray()) {
                return;
            }
            List<Long> failureIds = new ArrayList<>();
            idsNode.forEach(jsonNode -> {
                if (jsonNode.canConvertToLong()) {
                    failureIds.add(jsonNode.longValue());
                }
            });
            if (failureIds.isEmpty()) {
                return;
            }
            failureRepository.findAllById(failureIds).forEach(failure -> {
                failure.setStatus(ImportFailureStatus.RESOLVED);
                failure.setResolvedAt(now);
            });
        } catch (Exception e) {
            logger.warn("解析重试元数据失败: {}", e.getMessage());
        }
    }

    private void refreshTaskStatistics(ImportTask task) {
        int total = task.getItems().stream()
                .mapToInt(item -> safeInt(item.getTotalCount()))
                .max()
                .orElse(0);
        int successFromCompleted = task.getItems().stream()
                .filter(item -> item.getStatus() == ImportTaskItemStatus.COMPLETED)
                .mapToInt(item -> safeInt(item.getSuccessCount()))
                .sum();
        long unresolvedFailures = task.getFailures().stream()
                .filter(failure -> failure.getStatus() != ImportFailureStatus.RESOLVED)
                .count();
        int unresolved = unresolvedFailures > Integer.MAX_VALUE
                ? Integer.MAX_VALUE
                : (int) unresolvedFailures;
        int successBasedOnFailures = Math.max(0, total - unresolved);
        int success = Math.min(total, Math.max(successFromCompleted, successBasedOnFailures));
        task.setTotalCount(total);
        task.setFailureCount(unresolved);
        task.setSuccessCount(success);
    }

    private int safeInt(Integer value) {
        return value != null ? value : 0;
    }

    private record TaskExecutionContext(
            Long taskId,
            String taskCode,
            String importType,
            Long itemId,
            String fileName,
            String contentType,
            String optionsJson,
            byte[] fileContent
    ) {
    }
}


