package com.sambound.erp.dto;

import java.time.LocalDateTime;
import java.util.Set;

public record UserResponse(
    Long id,
    String username,
    String email,
    String fullName,
    Boolean enabled,
    Boolean accountNonExpired,
    Boolean accountNonLocked,
    Boolean credentialsNonExpired,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    Set<RoleSummary> roles
) {
    public record RoleSummary(
        Long id,
        String name,
        String description
    ) {}
}

