package com.sambound.erp.service;

import com.sambound.erp.entity.User;
import com.sambound.erp.exception.BusinessException;
import com.sambound.erp.repository.UserRepository;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    
    private static final String USER_CACHE_PREFIX = "user:";
    private static final String USER_USERNAME_CACHE_PREFIX = USER_CACHE_PREFIX + "username:";
    private static final String USER_ID_CACHE_PREFIX = USER_CACHE_PREFIX + "id:";
    private static final long CACHE_TTL_MINUTES = 30;

    public UserService(UserRepository userRepository, RedisTemplate<String, Object> redisTemplate) {
        this.userRepository = userRepository;
        this.redisTemplate = redisTemplate;
    }

    public User findByUsername(String username) {
        String cacheKey = USER_USERNAME_CACHE_PREFIX + username;
        
        // 尝试从缓存获取
        Optional<User> cachedUser = Optional.ofNullable((User) redisTemplate.opsForValue().get(cacheKey));
        if (cachedUser.isPresent()) {
            return cachedUser.get();
        }
        
        // 缓存未命中，从数据库查询
        User user = userRepository.findByUsernameWithRolesAndPermissions(username)
                .orElseThrow(() -> new BusinessException("用户不存在"));
        
        // 写入缓存
        redisTemplate.opsForValue().set(cacheKey, user, CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        
        // 同时缓存 ID 索引
        if (user.getId() != null) {
            String idCacheKey = USER_ID_CACHE_PREFIX + user.getId();
            redisTemplate.opsForValue().set(idCacheKey, user, CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        }
        
        return user;
    }

    public User findById(Long id) {
        String cacheKey = USER_ID_CACHE_PREFIX + id;
        
        // 尝试从缓存获取
        Optional<User> cachedUser = Optional.ofNullable((User) redisTemplate.opsForValue().get(cacheKey));
        if (cachedUser.isPresent()) {
            return cachedUser.get();
        }
        
        // 缓存未命中，从数据库查询
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException("用户不存在"));
        
        // 写入缓存
        redisTemplate.opsForValue().set(cacheKey, user, CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        
        // 同时缓存用户名索引
        if (user.getUsername() != null) {
            String usernameCacheKey = USER_USERNAME_CACHE_PREFIX + user.getUsername();
            redisTemplate.opsForValue().set(usernameCacheKey, user, CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        }
        
        return user;
    }
    
    /**
     * 清除用户缓存
     * @param userId 用户ID
     */
    void evictUserCache(Long userId) {
        String idCacheKey = USER_ID_CACHE_PREFIX + userId;
        User user = (User) redisTemplate.opsForValue().get(idCacheKey);
        
        // 删除ID缓存
        redisTemplate.delete(idCacheKey);
        
        // 如果知道用户名，也删除用户名缓存
        if (user != null && user.getUsername() != null) {
            String usernameCacheKey = USER_USERNAME_CACHE_PREFIX + user.getUsername();
            redisTemplate.delete(usernameCacheKey);
        }
    }
    
    /**
     * 清除用户缓存（通过用户名）
     * @param username 用户名
     */
    void evictUserCacheByUsername(String username) {
        String usernameCacheKey = USER_USERNAME_CACHE_PREFIX + username;
        User user = (User) redisTemplate.opsForValue().get(usernameCacheKey);
        
        // 删除用户名缓存
        redisTemplate.delete(usernameCacheKey);
        
        // 如果知道ID，也删除ID缓存
        if (user != null && user.getId() != null) {
            String idCacheKey = USER_ID_CACHE_PREFIX + user.getId();
            redisTemplate.delete(idCacheKey);
        }
    }

    public boolean existsByUsername(String username) {
        return userRepository.existsByUsername(username);
    }

    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }
}

