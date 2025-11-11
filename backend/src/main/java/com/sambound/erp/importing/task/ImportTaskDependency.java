package com.sambound.erp.importing.task;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 导入任务之间的依赖关系。
 */
@Entity
@Table(name = "import_task_dependencies")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportTaskDependency {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 当前任务。
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false)
    private ImportTask task;

    /**
     * 依赖的前序任务。
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "depends_on_id", nullable = false)
    private ImportTask dependsOn;

    /**
     * 创建时间。
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}


