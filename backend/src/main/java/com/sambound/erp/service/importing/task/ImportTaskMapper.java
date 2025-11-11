package com.sambound.erp.service.importing.task;

import com.sambound.erp.dto.ImportTaskCreateResponse;
import com.sambound.erp.dto.ImportTaskDetail;
import com.sambound.erp.dto.ImportTaskFailureDTO;
import com.sambound.erp.dto.ImportTaskItemSummary;
import com.sambound.erp.dto.ImportTaskSummary;
import com.sambound.erp.importing.task.ImportTask;
import com.sambound.erp.importing.task.ImportTaskFailure;
import com.sambound.erp.importing.task.ImportTaskItem;

import java.util.Comparator;
import java.util.List;

public final class ImportTaskMapper {

    private ImportTaskMapper() {
    }

    public static ImportTaskCreateResponse toCreateResponse(ImportTask task) {
        return new ImportTaskCreateResponse(
                task.getId(),
                task.getTaskCode(),
                task.getImportType(),
                task.getStatus().name(),
                task.getSourceFileName(),
                task.getCreatedBy(),
                task.getCreatedAt());
    }

    public static ImportTaskSummary toSummary(ImportTask task) {
        return new ImportTaskSummary(
                task.getId(),
                task.getTaskCode(),
                task.getImportType(),
                task.getStatus().name(),
                task.getSourceFileName(),
                task.getCreatedBy(),
                task.getTotalCount(),
                task.getSuccessCount(),
                task.getFailureCount(),
                task.getCreatedAt(),
                task.getStartedAt(),
                task.getCompletedAt());
    }

    public static ImportTaskItemSummary toItemSummary(ImportTaskItem item) {
        return new ImportTaskItemSummary(
                item.getId(),
                item.getSequenceNo(),
                item.getStatus().name(),
                item.getSourceFileName(),
                item.getTotalCount(),
                item.getSuccessCount(),
                item.getFailureCount(),
                item.getFailureReason(),
                item.getCreatedAt(),
                item.getStartedAt(),
                item.getCompletedAt());
    }

    public static ImportTaskDetail toDetail(ImportTask task) {
        List<ImportTaskItemSummary> items = task.getItems().stream()
                .sorted(Comparator.comparingInt(ImportTaskItem::getSequenceNo))
                .map(ImportTaskMapper::toItemSummary)
                .toList();
        return new ImportTaskDetail(toSummary(task), items);
    }

    public static ImportTaskFailureDTO toFailureDTO(ImportTaskFailure failure) {
        return new ImportTaskFailureDTO(
                failure.getId(),
                failure.getSection(),
                failure.getRowNumber(),
                failure.getField(),
                failure.getMessage(),
                failure.getStatus().name(),
                failure.getRawPayload(),
                failure.getCreatedAt(),
                failure.getResolvedAt());
    }
}


