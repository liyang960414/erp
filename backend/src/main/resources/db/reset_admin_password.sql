-- ============================================
-- 重置admin用户密码脚本
-- 密码: admin123
-- BCrypt哈希值（使用BCryptPasswordEncoder生成）
-- ============================================

-- 重要说明：
-- 1. BCrypt每次生成的哈希值都不同（因为使用随机盐），但都可以验证同一密码
-- 2. 此脚本使用的哈希值与 V2__init_data.sql 中的哈希值一致
-- 3. 如果使用重置API (POST /api/dev/reset-admin-password)，会生成新的哈希值
--    这是正常的，两种哈希值都可以验证密码 "admin123"
--
-- 如果需要生成新的哈希值，可以在Spring Boot应用中运行以下代码：
-- BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
-- String hash = encoder.encode("admin123");
-- System.out.println(hash);

-- 方法1：直接更新密码（使用已验证的BCrypt哈希）
UPDATE users 
SET password = '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iwKkg0.i'
WHERE username = 'admin';

-- 方法2：如果需要重新生成，请先删除admin用户，然后重新插入
-- DELETE FROM user_roles WHERE user_id IN (SELECT id FROM users WHERE username = 'admin');
-- DELETE FROM users WHERE username = 'admin';
-- 然后重新执行V2__init_data.sql中的admin用户插入语句

-- 验证更新
SELECT 
    username,
    password,
    CASE 
        WHEN password = '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iwKkg0.i' 
        THEN '密码已更新' 
        ELSE '密码未更新' 
    END AS status
FROM users 
WHERE username = 'admin';

