package com.sambound.erp.repository;

import com.sambound.erp.importing.task.ImportTask;
import com.sambound.erp.importing.task.ImportTaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ImportTaskRepository extends JpaRepository<ImportTask, Long>, JpaSpecificationExecutor<ImportTask> {

    Optional<ImportTask> findByTaskCode(String taskCode);

    List<ImportTask> findTop50ByStatusInOrderByCreatedAtAsc(Collection<ImportTaskStatus> statuses);

    boolean existsByImportTypeAndStatusIn(String importType, Collection<ImportTaskStatus> statuses);

    long countByStatus(ImportTaskStatus status);

    long countByImportTypeAndStatus(String importType, ImportTaskStatus status);

    List<ImportTask> findByImportTypeAndStatusInOrderByCreatedAtAsc(String importType,
                                                                    Collection<ImportTaskStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from ImportTask t where t.id = :id")
    Optional<ImportTask> findByIdForUpdate(@Param("id") Long id);
}


