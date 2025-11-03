package com.sambound.erp.service;

import com.sambound.erp.dto.PermissionResponse;
import com.sambound.erp.entity.Permission;
import com.sambound.erp.repository.PermissionRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class PermissionService {

    private final PermissionRepository permissionRepository;

    public PermissionService(PermissionRepository permissionRepository) {
        this.permissionRepository = permissionRepository;
    }

    public Page<PermissionResponse> getAllPermissions(Pageable pageable) {
        return permissionRepository.findAll(pageable)
                .map(this::convertToPermissionResponse);
    }

    @Cacheable(cacheNames = "permissions", key = "'all'")
    public List<PermissionResponse> getAllPermissionsList() {
        return permissionRepository.findAll().stream()
                .map(this::convertToPermissionResponse)
                .collect(Collectors.toList());
    }

    @Cacheable(cacheNames = "permissions", key = "#id")
    public PermissionResponse getPermissionById(Long id) {
        Permission permission = permissionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("权限不存在"));
        return convertToPermissionResponse(permission);
    }

    private PermissionResponse convertToPermissionResponse(Permission permission) {
        return new PermissionResponse(
                permission.getId(),
                permission.getName(),
                permission.getDescription()
        );
    }
}

