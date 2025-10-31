package com.sambound.erp.config;

import com.sambound.erp.entity.User;
import com.sambound.erp.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private static final Logger logger = LoggerFactory.getLogger(UserDetailsServiceImpl.class);
    private final UserRepository userRepository;

    public UserDetailsServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        logger.debug("加载用户信息: {}", username);
        
        // 先使用简单查询确保密码字段正确加载
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> {
                    logger.error("用户不存在: {}", username);
                    return new UsernameNotFoundException("用户不存在: " + username);
                });
        
        // 保存原始密码字段，确保密码字段始终正确
        String originalPassword = user.getPassword();
        if (originalPassword == null || originalPassword.isEmpty()) {
            logger.error("用户 {} 密码字段为空，这会导致认证失败", username);
        }
        
        // 使用JOIN FETCH加载角色和权限
        User userWithRoles = userRepository.findByUsernameWithRolesAndPermissions(username)
                .orElse(null);
        
        // 如果JOIN FETCH成功，使用包含角色和权限的用户对象，但强制使用原始密码
        if (userWithRoles != null && userWithRoles != user) {
            // 关键修复：无论JOIN FETCH返回什么，都强制使用原始密码字段
            userWithRoles.setPassword(originalPassword);
            user = userWithRoles;
        }
        
        // 最终验证：确保密码字段始终是原始密码
        if (originalPassword != null && !originalPassword.equals(user.getPassword())) {
            logger.error("用户 {} 密码字段被修改，强制恢复为原始密码", username);
            user.setPassword(originalPassword);
        }
        
        // 触发权限加载（确保所有LAZY关联都已初始化）
        try {
            user.getAuthorities().size();
        } catch (Exception e) {
            logger.warn("加载用户 {} 权限时出错: {}", username, e.getMessage());
        }
        
        // 在返回前最后一次验证和修复密码字段
        if (user.getPassword() == null || user.getPassword().isEmpty() || 
            (originalPassword != null && !originalPassword.equals(user.getPassword()))) {
            logger.error("用户 {} 密码字段异常，强制恢复为原始密码", username);
            user.setPassword(originalPassword);
        }
        
        return user;
    }
}

