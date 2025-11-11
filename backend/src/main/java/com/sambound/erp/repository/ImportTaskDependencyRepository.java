package com.sambound.erp.repository;

import com.sambound.erp.importing.task.ImportTaskDependency;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ImportTaskDependencyRepository extends JpaRepository<ImportTaskDependency, Long> {

    List<ImportTaskDependency> findByTaskId(Long taskId);

    List<ImportTaskDependency> findByDependsOnId(Long dependsOnId);

    void deleteByTaskId(Long taskId);
}


