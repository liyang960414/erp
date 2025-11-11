package com.sambound.erp.repository;

import com.sambound.erp.importing.task.ImportTaskItem;
import com.sambound.erp.importing.task.ImportTaskItemStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ImportTaskItemRepository extends JpaRepository<ImportTaskItem, Long> {

    List<ImportTaskItem> findByStatusInOrderByCreatedAtAsc(List<ImportTaskItemStatus> statuses);

    List<ImportTaskItem> findByTaskIdOrderBySequenceNo(Long taskId);
}


