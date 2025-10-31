package com.sambound.erp.dto;

import java.util.Set;

public record RoleResponse(
    Long id,
    String name,
    String description,
    Set<PermissionSummary> permissions
) {
    public record PermissionSummary(
        Long id,
        String name,
        String description
    ) {}
}

