package com.sambound.erp.service;

import com.sambound.erp.entity.User;
import com.sambound.erp.exception.BusinessException;
import com.sambound.erp.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User findByUsername(String username) {
        // 使用JOIN FETCH确保加载角色和权限，用于返回给前端
        return userRepository.findByUsernameWithRolesAndPermissions(username)
                .orElseThrow(() -> new BusinessException("用户不存在"));
    }

    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new BusinessException("用户不存在"));
    }

    public boolean existsByUsername(String username) {
        return userRepository.existsByUsername(username);
    }

    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }
}

