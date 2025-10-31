package com.sambound.erp.service;

import com.sambound.erp.dto.CreateUserRequest;
import com.sambound.erp.dto.RoleResponse;
import com.sambound.erp.dto.UpdateUserRequest;
import com.sambound.erp.dto.UserResponse;
import com.sambound.erp.entity.AuditLog;
import com.sambound.erp.entity.Role;
import com.sambound.erp.entity.User;
import com.sambound.erp.exception.BusinessException;
import com.sambound.erp.repository.RoleRepository;
import com.sambound.erp.repository.UserRepository;
import com.sambound.erp.util.AuditLogHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
public class UserManagementService {

    private static final Logger logger = LoggerFactory.getLogger(UserManagementService.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;

    public UserManagementService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            AuditLogService auditLogService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditLogService = auditLogService;
    }

    public Page<UserResponse> getAllUsers(Pageable pageable) {
        return userRepository.findAll(pageable)
                .map(this::convertToUserResponse);
    }

    public UserResponse getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException("用户不存在"));
        return convertToUserResponse(user);
    }

    public UserResponse createUser(CreateUserRequest request) {
        logger.debug("创建用户：{}", request.username());
        
        if (userRepository.existsByUsername(request.username())) {
            throw new BusinessException("用户名已存在");
        }

        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException("邮箱已被注册");
        }

        User user = User.builder()
                .username(request.username())
                .password(passwordEncoder.encode(request.password()))
                .email(request.email())
                .fullName(request.fullName())
                .enabled(request.enabled() != null ? request.enabled() : true)
                .accountNonExpired(true)
                .accountNonLocked(true)
                .credentialsNonExpired(true)
                .build();

        // 分配角色
        if (request.roleNames() != null && !request.roleNames().isEmpty()) {
            Set<Role> roles = request.roleNames().stream()
                    .map(roleName -> roleRepository.findByName(roleName)
                            .orElseThrow(() -> new BusinessException("角色不存在: " + roleName)))
                    .collect(Collectors.toSet());
            user.setRoles(roles);
        }

        User savedUser = userRepository.save(user);
        logger.info("用户 {} 创建成功，ID: {}", savedUser.getUsername(), savedUser.getId());
        return convertToUserResponse(savedUser);
    }

    public UserResponse updateUser(Long id, UpdateUserRequest request) {
        logger.debug("更新用户：ID {}", id);
        
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException("用户不存在"));

        // 更新邮箱
        if (request.email() != null && !request.email().equals(user.getEmail())) {
            if (userRepository.existsByEmail(request.email())) {
                throw new BusinessException("邮箱已被使用");
            }
            user.setEmail(request.email());
        }

        // 更新其他字段
        if (request.fullName() != null) {
            user.setFullName(request.fullName());
        }
        if (request.enabled() != null) {
            user.setEnabled(request.enabled());
        }
        if (request.accountNonExpired() != null) {
            user.setAccountNonExpired(request.accountNonExpired());
        }
        if (request.accountNonLocked() != null) {
            user.setAccountNonLocked(request.accountNonLocked());
        }
        if (request.credentialsNonExpired() != null) {
            user.setCredentialsNonExpired(request.credentialsNonExpired());
        }

        // 更新角色
        if (request.roleNames() != null) {
            Set<Role> roles = request.roleNames().stream()
                    .map(roleName -> roleRepository.findByName(roleName)
                            .orElseThrow(() -> new BusinessException("角色不存在: " + roleName)))
                    .collect(Collectors.toSet());
            user.setRoles(roles);
        }

        User savedUser = userRepository.save(user);
        logger.info("用户 {} 更新成功", savedUser.getUsername());
        return convertToUserResponse(savedUser);
    }

    public void deleteUser(Long id) {
        logger.debug("删除用户：ID {}", id);
        
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException("用户不存在"));
        String username = user.getUsername();
        userRepository.delete(user);
        logger.info("用户 {} 删除成功", username);
    }

    public void changePassword(Long id, String newPassword) {
        logger.debug("修改用户密码：ID {}", id);
        
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException("用户不存在"));
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        logger.info("用户 {} 密码修改成功", user.getUsername());
    }

    private UserResponse convertToUserResponse(User user) {
        Set<UserResponse.RoleSummary> roleSummaries = user.getRoles().stream()
                .map(role -> new UserResponse.RoleSummary(
                        role.getId(),
                        role.getName(),
                        role.getDescription()
                ))
                .collect(Collectors.toSet());

        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFullName(),
                user.getEnabled(),
                user.getAccountNonExpired(),
                user.getAccountNonLocked(),
                user.getCredentialsNonExpired(),
                user.getCreatedAt(),
                user.getUpdatedAt(),
                roleSummaries
        );
    }
}

