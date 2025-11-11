package com.sambound.erp.repository;

import com.sambound.erp.importing.task.ImportFailureStatus;
import com.sambound.erp.importing.task.ImportTaskFailure;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportTaskFailureRepository extends JpaRepository<ImportTaskFailure, Long> {

    Page<ImportTaskFailure> findByTaskId(Long taskId, Pageable pageable);

    Page<ImportTaskFailure> findByTaskIdAndStatus(Long taskId, ImportFailureStatus status, Pageable pageable);
}


