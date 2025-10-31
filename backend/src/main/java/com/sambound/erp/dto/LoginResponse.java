package com.sambound.erp.dto;

import java.util.Set;

public record LoginResponse(
    String token,
    String tokenType,
    Long userId,
    String username,
    String email,
    String fullName,
    Set<String> roles
) {}

