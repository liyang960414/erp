package com.sambound.erp.service.importing.task;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sambound.erp.importing.task.ImportDependencyProperties;
import com.sambound.erp.importing.task.ImportFailureStatus;
import com.sambound.erp.importing.task.ImportTask;
import com.sambound.erp.importing.task.ImportTaskDependency;
import com.sambound.erp.importing.task.ImportTaskItem;
import com.sambound.erp.importing.task.ImportTaskItemStatus;
import com.sambound.erp.importing.task.ImportTaskStatus;
import com.sambound.erp.repository.ImportTaskFailureRepository;
import com.sambound.erp.repository.ImportTaskDependencyRepository;
import com.sambound.erp.repository.ImportTaskRepository;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Comparator;

/**
 * 导入任务创建与管理服务。
 */
@Service
public class ImportTaskManager {

    private static final Logger logger = LoggerFactory.getLogger(ImportTaskManager.class);
    private static final DateTimeFormatter CODE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final EnumSet<ImportTaskStatus> DEPENDENCY_STATUSES = EnumSet.of(
            ImportTaskStatus.WAITING,
            ImportTaskStatus.QUEUED,
            ImportTaskStatus.RUNNING,
            ImportTaskStatus.FAILED
    );

    private final ImportTaskRepository taskRepository;
    private final ImportTaskDependencyRepository dependencyRepository;
    private final ImportTaskFailureRepository failureRepository;
    private final ImportDependencyProperties dependencyProperties;
    private final ObjectMapper objectMapper;

    public ImportTaskManager(ImportTaskRepository taskRepository,
                             ImportTaskDependencyRepository dependencyRepository,
                             ImportTaskFailureRepository failureRepository,
                             ImportDependencyProperties dependencyProperties,
                             ObjectMapper objectMapper) {
        this.taskRepository = taskRepository;
        this.dependencyRepository = dependencyRepository;
        this.failureRepository = failureRepository;
        this.dependencyProperties = dependencyProperties;
        this.objectMapper = objectMapper;
    }

    /**
     * 创建新的导入任务。
     *
     * @param importType 导入类型
     * @param file       上传文件
     * @param createdBy  发起人
     * @param options    附加参数
     * @return 创建后的任务实体
     */
    @Transactional
    public ImportTask createTask(String importType,
                                 MultipartFile file,
                                 String createdBy,
                                 Map<String, Object> options) {
        Objects.requireNonNull(importType, "importType");
        Objects.requireNonNull(file, "file");
        String fileName = file.getOriginalFilename();
        try {
            ImportTask task = ImportTask.builder()
                    .taskCode(generateTaskCode(importType))
                    .importType(importType)
                    .status(ImportTaskStatus.WAITING)
                    .createdBy(createdBy)
                    .sourceFileName(fileName)
                    .optionsJson(serializeOptions(options))
                    .build();

            ImportTaskItem item = ImportTaskItem.builder()
                    .task(task)
                    .sequenceNo(1)
                    .status(ImportTaskItemStatus.PENDING)
                    .sourceFileName(fileName)
                    .contentType(file.getContentType())
                    .fileContent(file.getBytes())
                    .build();

            task.getItems().add(item);
            taskRepository.save(task);
            registerDependencies(task);
            logger.info("创建导入任务成功: type={}, code={}, file={}", importType, task.getTaskCode(), fileName);
            return task;
        } catch (IOException e) {
            throw new IllegalStateException("读取上传文件失败: " + e.getMessage(), e);
        }
    }

    @Transactional(readOnly = true)
    public Page<ImportTask> searchTasks(String importType,
                                        ImportTaskStatus status,
                                        String createdBy,
                                        Pageable pageable) {
        Specification<ImportTask> spec = Specification.where(null);
        if (importType != null && !importType.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("importType"), importType));
        }
        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (createdBy != null && !createdBy.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("createdBy"), createdBy));
        }
        return taskRepository.findAll(spec, pageable);
    }

    @Transactional(readOnly = true)
    public ImportTask getTask(Long taskId) {
        return taskRepository.findById(taskId)
                .orElseThrow(() -> new EntityNotFoundException("导入任务不存在: " + taskId));
    }

    @Transactional(readOnly = true)
    public Page<com.sambound.erp.importing.task.ImportTaskFailure> findFailures(Long taskId,
                                                                                ImportFailureStatus status,
                                                                                Pageable pageable) {
        if (status == null) {
            return failureRepository.findByTaskId(taskId, pageable);
        }
        return failureRepository.findByTaskIdAndStatus(taskId, status, pageable);
    }

    @Transactional
    public ImportTask retryTask(Long taskId,
                                MultipartFile file,
                                String createdBy,
                                List<Long> failureIds) {
        ImportTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new EntityNotFoundException("导入任务不存在: " + taskId));
        try {
            int nextSequence = task.getItems().stream()
                    .map(ImportTaskItem::getSequenceNo)
                    .max(Integer::compareTo)
                    .orElse(0) + 1;
            ImportTaskItem previousItem = task.getItems().stream()
                    .max(Comparator.comparingInt(ImportTaskItem::getSequenceNo))
                    .orElse(null);
            String retryMetadata = createRetryMetadata(failureIds, createdBy);
            ImportTaskItem newItem = ImportTaskItem.builder()
                    .task(task)
                    .sequenceNo(nextSequence)
                    .status(ImportTaskItemStatus.PENDING)
                    .sourceFileName(file.getOriginalFilename())
                    .contentType(file.getContentType())
                    .fileContent(file.getBytes())
                    .payloadJson(retryMetadata)
                    .retryOf(previousItem)
                    .build();
            task.getItems().add(newItem);
            task.setStatus(ImportTaskStatus.QUEUED);
            task.setScheduledAt(LocalDateTime.now());
            taskRepository.save(task);
            if (failureIds != null && !failureIds.isEmpty()) {
                failureRepository.findAllById(failureIds).forEach(failure -> {
                    failure.setStatus(ImportFailureStatus.RESUBMITTED);
                    failure.setResolvedAt(null);
                });
            }
            return task;
        } catch (IOException e) {
            throw new IllegalStateException("读取重试文件失败: " + e.getMessage(), e);
        }
    }

    private void registerDependencies(ImportTask task) {
        List<String> dependencyTypes = dependencyProperties.getDependenciesFor(task.getImportType());
        if (dependencyTypes == null || dependencyTypes.isEmpty()) {
            // 没有依赖则直接排队
            task.setStatus(ImportTaskStatus.QUEUED);
            task.setScheduledAt(LocalDateTime.now());
            return;
        }
        Set<Long> dependencyIds = new HashSet<>();
        for (String dependencyType : dependencyTypes) {
            List<ImportTask> blockingTasks = taskRepository.findByImportTypeAndStatusInOrderByCreatedAtAsc(
                    dependencyType, DEPENDENCY_STATUSES);
            for (ImportTask blockingTask : blockingTasks) {
                if (Objects.equals(blockingTask.getId(), task.getId())) {
                    continue;
                }
                if (!dependencyIds.add(blockingTask.getId())) {
                    continue;
                }
                ImportTaskDependency dependency = ImportTaskDependency.builder()
                        .task(task)
                        .dependsOn(blockingTask)
                        .build();
                task.getDependencies().add(dependency);
            }
        }
        if (task.getDependencies().isEmpty()) {
            task.setStatus(ImportTaskStatus.QUEUED);
            task.setScheduledAt(LocalDateTime.now());
        } else {
            dependencyRepository.saveAll(task.getDependencies());
            logger.info("任务 {} 注册 {} 个依赖", task.getTaskCode(), task.getDependencies().size());
        }
    }

    private String serializeOptions(Map<String, Object> options) {
        if (options == null || options.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(options);
        } catch (JsonProcessingException e) {
            logger.warn("导入任务参数序列化失败: {}", e.getMessage());
            return null;
        }
    }

    private String generateTaskCode(String importType) {
        String prefix = importType.replaceAll("[^a-zA-Z0-9]", "").toUpperCase();
        String timestamp = CODE_TIME_FORMATTER.format(LocalDateTime.now());
        String random = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 4).toUpperCase();
        return prefix + "-" + timestamp + "-" + random;
    }

    private String createRetryMetadata(List<Long> failureIds, String createdBy) {
        if ((failureIds == null || failureIds.isEmpty()) && (createdBy == null || createdBy.isBlank())) {
            return null;
        }
        try {
            var node = objectMapper.createObjectNode();
            if (failureIds != null && !failureIds.isEmpty()) {
                var array = node.putArray("retryFailureIds");
                failureIds.forEach(array::add);
            }
            if (createdBy != null && !createdBy.isBlank()) {
                node.put("requestedBy", createdBy);
            }
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            logger.warn("重试元数据序列化失败: {}", e.getMessage());
            return null;
        }
    }
}


