package com.sambound.erp.service;

import com.sambound.erp.config.JwtUtil;
import com.sambound.erp.dto.LoginRequest;
import com.sambound.erp.dto.LoginResponse;
import com.sambound.erp.dto.RegisterRequest;
import com.sambound.erp.entity.AuditLog;
import com.sambound.erp.entity.Role;
import com.sambound.erp.entity.User;
import com.sambound.erp.exception.BusinessException;
import com.sambound.erp.repository.RoleRepository;
import com.sambound.erp.repository.UserRepository;
import com.sambound.erp.util.AuditLogHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
public class AuthService {

    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;
    private final AuditLogService auditLogService;

    public AuthService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            JwtUtil jwtUtil,
            AuthenticationManager authenticationManager,
            AuditLogService auditLogService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.authenticationManager = authenticationManager;
        this.auditLogService = auditLogService;
    }

    public LoginResponse login(LoginRequest request) {
        logger.debug("用户尝试登录：{}", request.username());
        
        try {
            Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password())
            );

            User user = userRepository.findByUsername(request.username())
                    .orElseThrow(() -> new UsernameNotFoundException("用户不存在"));

            String token = jwtUtil.generateToken(user.getUsername(), user.getId());

            Set<String> roles = user.getRoles().stream()
                    .map(Role::getName)
                    .collect(Collectors.toSet());

            // 记录成功登录的审计日志
            AuditLog auditLog = AuditLogHelper.success()
                    .username(user.getUsername())
                    .userId(user.getId())
                    .action(AuditLogHelper.ACTION_LOGIN)
                    .module(AuditLogHelper.MODULE_AUTH)
                    .resourceType(AuditLogHelper.RESOURCE_TYPE_USER)
                    .resourceId(user.getId().toString())
                    .description("用户登录成功")
                    .build();
            auditLogService.saveAuditLogAsync(auditLog);
            
            logger.info("用户 {} 登录成功", user.getUsername());

            return new LoginResponse(
                token,
                "Bearer",
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFullName(),
                roles
            );
        } catch (Exception e) {
            // 记录失败登录的审计日志
            AuditLog auditLog = AuditLogHelper.failure("登录失败: " + e.getMessage())
                    .username(request.username())
                    .userId(null)
                    .action(AuditLogHelper.ACTION_LOGIN)
                    .module(AuditLogHelper.MODULE_AUTH)
                    .description("用户登录失败")
                    .build();
            auditLogService.saveAuditLogAsync(auditLog);
            
            logger.error("用户 {} 登录失败", request.username(), e);
            throw e;
        }
    }

    public User register(RegisterRequest request) {
        logger.debug("用户尝试注册：{}", request.username());
        
        if (userRepository.existsByUsername(request.username())) {
            throw new BusinessException("用户名已存在");
        }

        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException("邮箱已被注册");
        }

        // 创建新用户，默认分配USER角色
        Role defaultRole = roleRepository.findByName("USER")
                .orElseThrow(() -> new BusinessException("系统未配置默认角色"));

        User user = User.builder()
                .username(request.username())
                .password(passwordEncoder.encode(request.password()))
                .email(request.email())
                .fullName(request.fullName())
                .enabled(true)
                .accountNonExpired(true)
                .accountNonLocked(true)
                .credentialsNonExpired(true)
                .roles(Set.of(defaultRole))
                .build();

        User savedUser = userRepository.save(user);
        
        // 记录用户注册的审计日志
        AuditLog auditLog = AuditLogHelper.success()
                .username(savedUser.getUsername())
                .userId(savedUser.getId())
                .action(AuditLogHelper.ACTION_REGISTER)
                .module(AuditLogHelper.MODULE_AUTH)
                .resourceType(AuditLogHelper.RESOURCE_TYPE_USER)
                .resourceId(savedUser.getId().toString())
                .description("新用户注册成功")
                .build();
        auditLogService.saveAuditLogAsync(auditLog);
        
        logger.info("用户 {} 注册成功", savedUser.getUsername());
        
        return savedUser;
    }
}

