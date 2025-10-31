package com.sambound.erp.controller;

import com.sambound.erp.dto.ApiResponse;
import com.sambound.erp.entity.User;
import com.sambound.erp.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

/**
 * 临时密码重置控制器
 * 仅用于开发环境，重置admin用户的密码
 * 
 * 警告：生产环境中应删除或禁用此控制器
 */
@RestController
@RequestMapping("/api/dev")
public class PasswordResetController {

    private static final Logger logger = LoggerFactory.getLogger(PasswordResetController.class);
    
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public PasswordResetController(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 重置admin用户密码为admin123
     * POST /api/dev/reset-admin-password
     */
    @PostMapping("/reset-admin-password")
    public ResponseEntity<ApiResponse<String>> resetAdminPassword() {
        return userRepository.findByUsername("admin")
                .map(user -> {
                    String newPassword = "admin123";
                    String newPasswordHash = passwordEncoder.encode(newPassword);
                    
                    user.setPassword(newPasswordHash);
                    userRepository.save(user);
                    
                    logger.info("admin用户密码已重置");
                    
                    return ResponseEntity.ok(ApiResponse.<String>success("密码已重置为: admin123", null));
                })
                .orElse(ResponseEntity.ok(ApiResponse.<String>error("admin用户不存在")));
    }

    /**
     * 验证admin用户密码
     * GET /api/dev/verify-admin-password?password=admin123
     */
    @GetMapping("/verify-admin-password")
    public ResponseEntity<ApiResponse<String>> verifyAdminPassword(@RequestParam String password) {
        return userRepository.findByUsername("admin")
                .map(user -> {
                    boolean matches = passwordEncoder.matches(password, user.getPassword());
                    String message = matches 
                            ? "密码匹配 ✓" 
                            : "密码不匹配 ✗";
                    
                    return ResponseEntity.ok(ApiResponse.success(message, 
                            "存储的哈希: " + user.getPassword()));
                })
                .orElse(ResponseEntity.ok(ApiResponse.<String>error("admin用户不存在")));
    }
}

