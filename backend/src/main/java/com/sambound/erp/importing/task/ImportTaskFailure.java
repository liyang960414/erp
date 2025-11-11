package com.sambound.erp.importing.task;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 导入失败记录，用于后续查看与重试。
 */
@Entity
@Table(name = "import_task_failures")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportTaskFailure {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false)
    private ImportTask task;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_item_id")
    private ImportTaskItem taskItem;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ImportFailureStatus status;

    @Column(name = "section", length = 128)
    private String section;

    @Column(name = "row_number")
    private Integer rowNumber;

    @Column(name = "field_name", length = 128)
    private String field;

    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    /**
     * 行原始数据（JSON 字符串）。
     */
    @Column(name = "raw_payload", columnDefinition = "TEXT")
    private String rawPayload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @PrePersist
    public void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.status == null) {
            this.status = ImportFailureStatus.PENDING;
        }
    }
}


