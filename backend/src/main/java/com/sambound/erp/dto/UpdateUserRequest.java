package com.sambound.erp.dto;

import jakarta.validation.constraints.Email;
import java.util.Set;

public record UpdateUserRequest(
    @Email(message = "邮箱格式不正确")
    String email,
    
    String fullName,
    
    Boolean enabled,
    
    Boolean accountNonExpired,
    
    Boolean accountNonLocked,
    
    Boolean credentialsNonExpired,
    
    Set<String> roleNames
) {}

