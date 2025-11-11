package com.sambound.erp.dto;

import java.util.List;

public record ImportTaskDetail(
        ImportTaskSummary task,
        List<ImportTaskItemSummary> items
) {
}


