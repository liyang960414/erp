package com.sambound.erp.importing.task;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Excel 导入后台任务实体。
 */
@Entity
@Table(name = "import_tasks")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 业务唯一编号，便于日志追踪。
     */
    @Column(name = "task_code", nullable = false, unique = true, length = 64)
    private String taskCode;

    /**
     * 导入类型（如 unit、material、bom 等）。
     */
    @Column(name = "import_type", nullable = false, length = 64)
    private String importType;

    /**
     * 当前任务状态。
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ImportTaskStatus status;

    @Column(name = "created_by", length = 64)
    private String createdBy;

    /**
     * 上传文件原始名称。
     */
    @Column(name = "source_file_name", length = 256)
    private String sourceFileName;

    /**
     * 额外参数或配置（JSON 字符串）。
     */
    @Column(name = "options_json", columnDefinition = "TEXT")
    private String optionsJson;

    @Column(name = "total_count")
    private Integer totalCount;

    @Column(name = "success_count")
    private Integer successCount;

    @Column(name = "failure_count")
    private Integer failureCount;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "scheduled_at")
    private LocalDateTime scheduledAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Version
    private Long version;

    @OneToMany(mappedBy = "task", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ImportTaskDependency> dependencies = new ArrayList<>();

    @OneToMany(mappedBy = "dependsOn", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ImportTaskDependency> dependents = new ArrayList<>();

    @OneToMany(mappedBy = "task", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ImportTaskItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "task", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ImportTaskFailure> failures = new ArrayList<>();

    @PrePersist
    public void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.status == null) {
            this.status = ImportTaskStatus.WAITING;
        }
        if (this.totalCount == null) {
            this.totalCount = 0;
        }
        if (this.successCount == null) {
            this.successCount = 0;
        }
        if (this.failureCount == null) {
            this.failureCount = 0;
        }
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}


