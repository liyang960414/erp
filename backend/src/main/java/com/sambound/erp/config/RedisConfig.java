package com.sambound.erp.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Redis 缓存配置
 * 配置 Redis 连接、序列化和缓存策略
 */
@Configuration
@EnableCaching
public class RedisConfig {

    /**
     * 配置 RedisTemplate，用于手动操作 Redis
     * Key 使用 String 序列化，Value 使用 JSON 序列化
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // 配置 Key 序列化器（String）
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);

        // 配置 Value 序列化器（JSON）
        Jackson2JsonRedisSerializer<Object> jsonSerializer = createJackson2JsonRedisSerializer();
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        // 设置默认序列化器
        template.setDefaultSerializer(jsonSerializer);
        template.afterPropertiesSet();
        return template;
    }

    /**
     * 配置缓存管理器
     * 为不同的缓存名称设置不同的过期时间
     */
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        // 默认缓存配置：1小时过期
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofHours(1))
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(createJackson2JsonRedisSerializer()))
                .disableCachingNullValues();

        // 为不同缓存配置不同的过期时间
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();
        
        // 权限和角色：1小时
        cacheConfigurations.put("permissions", defaultConfig.entryTtl(Duration.ofHours(1)));
        cacheConfigurations.put("roles", defaultConfig.entryTtl(Duration.ofHours(1)));
        
        // 单位组和物料组：2小时（更新频率低）
        cacheConfigurations.put("unitGroups", defaultConfig.entryTtl(Duration.ofHours(2)));
        cacheConfigurations.put("materialGroups", defaultConfig.entryTtl(Duration.ofHours(2)));
        
        // 单位和物料：2小时（导入完成后更新频率低）
        cacheConfigurations.put("units", defaultConfig.entryTtl(Duration.ofHours(2)));
        cacheConfigurations.put("materials", defaultConfig.entryTtl(Duration.ofHours(2)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .transactionAware()
                .build();
    }

    /**
     * 创建 Jackson2JsonRedisSerializer
     * 配置 ObjectMapper 以正确处理序列化
     */
    private Jackson2JsonRedisSerializer<Object> createJackson2JsonRedisSerializer() {
        Jackson2JsonRedisSerializer<Object> serializer = new Jackson2JsonRedisSerializer<>(Object.class);
        
        ObjectMapper objectMapper = new ObjectMapper();
        // 设置可见性：字段、getter、setter、构造函数都可见
        objectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        // 不要求字段有 @JsonIgnoreProperties 注解
        objectMapper.deactivateDefaultTyping();
        
        serializer.setObjectMapper(objectMapper);
        return serializer;
    }
}

