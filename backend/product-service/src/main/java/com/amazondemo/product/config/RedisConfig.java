package com.amazondemo.product.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Redis Cache Configuration
 * ==========================
 * Configures different TTL (Time-To-Live) for different cache names.
 *
 * CACHE STRATEGY:
 * - "product" (single product): 5 minutes TTL
 * - "products" (product list): 1 minute TTL (changes more often)
 * - "categories": 1 hour TTL (rarely changes)
 *
 * WHY DIFFERENT TTLs?
 * - Product lists change frequently (stock, prices) -> short TTL
 * - Categories rarely change -> long TTL
 * - Individual products are moderately cached
 *
 * CACHE SERIALIZATION: JSON (human readable, compatible across services)
 */
@Configuration
public class RedisConfig {

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        // Configure ObjectMapper with JavaTimeModule to support LocalDateTime serialization
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapper.activateDefaultTyping(
                objectMapper.getPolymorphicTypeValidator(),
                ObjectMapper.DefaultTyping.NON_FINAL
        );

        // Default config - JSON serialization
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer(objectMapper)))
                .disableCachingNullValues();

        // Custom TTLs per cache
        Map<String, RedisCacheConfiguration> cacheConfigs = new HashMap<>();
        cacheConfigs.put("product", defaultConfig.entryTtl(Duration.ofMinutes(5)));
        cacheConfigs.put("products", defaultConfig.entryTtl(Duration.ofMinutes(1)));
        cacheConfigs.put("categories", defaultConfig.entryTtl(Duration.ofHours(1)));
        cacheConfigs.put("featured", defaultConfig.entryTtl(Duration.ofMinutes(10)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig.entryTtl(Duration.ofMinutes(5)))
                .withInitialCacheConfigurations(cacheConfigs)
                .build();
    }
}
