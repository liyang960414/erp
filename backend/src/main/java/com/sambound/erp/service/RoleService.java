package com.sambound.erp.service;

import com.sambound.erp.dto.CreateRoleRequest;
import com.sambound.erp.dto.PermissionResponse;
import com.sambound.erp.dto.RoleResponse;
import com.sambound.erp.dto.UpdateRoleRequest;
import com.sambound.erp.entity.Permission;
import com.sambound.erp.entity.Role;
import com.sambound.erp.exception.BusinessException;
import com.sambound.erp.repository.PermissionRepository;
import com.sambound.erp.repository.RoleRepository;
import com.sambound.erp.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class RoleService {

    private static final Logger logger = LoggerFactory.getLogger(RoleService.class);

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final UserRepository userRepository;

    public RoleService(
            RoleRepository roleRepository,
            PermissionRepository permissionRepository,
            UserRepository userRepository) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.userRepository = userRepository;
    }

    public Page<RoleResponse> getAllRoles(Pageable pageable) {
        return roleRepository.findAll(pageable)
                .map(this::convertToRoleResponse);
    }

    @Cacheable(cacheNames = "roles", key = "'all'")
    public List<RoleResponse> getAllRolesList() {
        return roleRepository.findAll().stream()
                .map(this::convertToRoleResponse)
                .collect(Collectors.toList());
    }

    @Cacheable(cacheNames = "roles", key = "#id")
    public RoleResponse getRoleById(Long id) {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new BusinessException("角色不存在"));
        return convertToRoleResponse(role);
    }

    @Transactional
    @CacheEvict(cacheNames = "roles", allEntries = true)
    public RoleResponse createRole(CreateRoleRequest request) {
        logger.debug("创建角色：{}", request.name());
        
        if (roleRepository.existsByName(request.name())) {
            throw new BusinessException("角色已存在");
        }

        Role role = Role.builder()
                .name(request.name())
                .description(request.description())
                .permissions(new HashSet<>())
                .build();

        // 分配权限
        if (request.permissionNames() != null && !request.permissionNames().isEmpty()) {
            Set<Permission> permissions = request.permissionNames().stream()
                    .map(permName -> permissionRepository.findByName(permName)
                            .orElseThrow(() -> new BusinessException("权限不存在: " + permName)))
                    .collect(Collectors.toSet());
            role.setPermissions(permissions);
        }

        Role savedRole = roleRepository.save(role);
        logger.info("角色 {} 创建成功，ID: {}", savedRole.getName(), savedRole.getId());
        return convertToRoleResponse(savedRole);
    }

    @Transactional
    @CacheEvict(cacheNames = "roles", allEntries = true)
    public RoleResponse updateRole(Long id, UpdateRoleRequest request) {
        logger.debug("更新角色：ID {}", id);
        
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new BusinessException("角色不存在"));

        if (request.description() != null) {
            role.setDescription(request.description());
        }

        // 更新权限
        if (request.permissionNames() != null) {
            Set<Permission> permissions = request.permissionNames().stream()
                    .map(permName -> permissionRepository.findByName(permName)
                            .orElseThrow(() -> new BusinessException("权限不存在: " + permName)))
                    .collect(Collectors.toSet());
            role.setPermissions(permissions);
        }

        Role savedRole = roleRepository.save(role);
        logger.info("角色 {} 更新成功", savedRole.getName());
        return convertToRoleResponse(savedRole);
    }

    @Transactional
    @CacheEvict(cacheNames = "roles", allEntries = true)
    public void deleteRole(Long id) {
        logger.debug("删除角色：ID {}", id);
        
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new BusinessException("角色不存在"));

        // 检查是否有用户使用该角色
        if (userRepository.existsByRolesContaining(role)) {
            throw new BusinessException("该角色正在被使用，无法删除");
        }

        String roleName = role.getName();
        roleRepository.delete(role);
        logger.info("角色 {} 删除成功", roleName);
    }

    private RoleResponse convertToRoleResponse(Role role) {
        Set<RoleResponse.PermissionSummary> permissionSummaries = role.getPermissions().stream()
                .map(perm -> new RoleResponse.PermissionSummary(
                        perm.getId(),
                        perm.getName(),
                        perm.getDescription()
                ))
                .collect(Collectors.toSet());

        return new RoleResponse(
                role.getId(),
                role.getName(),
                role.getDescription(),
                permissionSummaries
        );
    }
}

