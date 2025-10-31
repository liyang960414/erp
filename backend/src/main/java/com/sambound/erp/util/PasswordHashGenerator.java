package com.sambound.erp.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 密码哈希生成工具
 * 用于生成BCrypt密码哈希值，用于数据库初始化
 * 
 * 使用方法：
 * 运行main方法，会输出 "admin123" 的BCrypt哈希值
 */
public class PasswordHashGenerator {
    
    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        
        String password = "admin123";
        String hash = encoder.encode(password);
        
        System.out.println("========================================");
        System.out.println("密码: " + password);
        System.out.println("BCrypt哈希: " + hash);
        System.out.println("========================================");
        
        // 验证哈希
        boolean matches = encoder.matches(password, hash);
        System.out.println("验证结果: " + (matches ? "✓ 匹配" : "✗ 不匹配"));
        
        // 生成多个哈希值供选择
        System.out.println("\n生成5个不同的哈希值（BCrypt每次生成都不同，但都可以验证同一密码）:");
        for (int i = 1; i <= 5; i++) {
            String newHash = encoder.encode(password);
            boolean verify = encoder.matches(password, newHash);
            System.out.println(i + ". " + newHash + " (验证: " + (verify ? "✓" : "✗") + ")");
        }
    }
}

