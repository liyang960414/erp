package com.sambound.erp.dto;

import java.util.Set;

public record UpdateRoleRequest(
    String description,
    Set<String> permissionNames
) {}

