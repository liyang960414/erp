package com.sambound.erp.importing.task;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 导入任务子项，通常对应一次文件导入或失败重试。
 */
@Entity
@Table(name = "import_task_items")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportTaskItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false)
    private ImportTask task;

    /**
     * 顺序号，便于按创建顺序执行。
     */
    @Column(name = "sequence_no", nullable = false)
    private Integer sequenceNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ImportTaskItemStatus status;

    @Column(name = "source_file_name", length = 256)
    private String sourceFileName;

    @Column(name = "content_type", length = 128)
    private String contentType;

    @Lob
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "file_content", columnDefinition = "BYTEA")
    private byte[] fileContent;

    /**
     * 额外的参数或过滤条件（JSON 字符串）。
     */
    @Column(name = "payload_json", columnDefinition = "TEXT")
    private String payloadJson;

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

    /**
     * 重试来源的子项。
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "retry_of_item_id")
    private ImportTaskItem retryOf;

    @OneToMany(mappedBy = "retryOf", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ImportTaskItem> retryChildren = new ArrayList<>();

    @OneToMany(mappedBy = "taskItem", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ImportTaskFailure> failures = new ArrayList<>();

    @Version
    private Long version;

    @PrePersist
    public void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.status == null) {
            this.status = ImportTaskItemStatus.PENDING;
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


