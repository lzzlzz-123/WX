package com.buildmart.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class RedisCacheService {
    private static final Logger log = LoggerFactory.getLogger(RedisCacheService.class);
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public RedisCacheService(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    public String getRaw(String key) {
        try {
            return redis.opsForValue().get(key);
        } catch (RuntimeException ex) {
            log.warn("Redis read skipped for key {}: {}", key, ex.getMessage());
            return null;
        }
    }

    public boolean hasKey(String key) {
        try {
            return Boolean.TRUE.equals(redis.hasKey(key));
        } catch (RuntimeException ex) {
            log.warn("Redis existence check skipped for key {}: {}", key, ex.getMessage());
            return false;
        }
    }

    public void putRaw(String key, String value, Duration ttl) {
        try {
            redis.opsForValue().set(key, value, ttl);
        } catch (RuntimeException ex) {
            log.warn("Redis write skipped for key {}: {}", key, ex.getMessage());
        }
    }

    /**
     * SET NX EX 语义：key 不存在才写入。用于幂等占位（如支付回调按微信交易号去重）。
     * Redis 故障时返回 false 表示占位失败，调用方应回源数据库做幂等兜底判断。
     */
    public boolean setIfAbsent(String key, String value, Duration ttl) {
        try {
            return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key, value, ttl));
        } catch (RuntimeException ex) {
            log.warn("Redis setIfAbsent skipped for key {}: {}", key, ex.getMessage());
            return false;
        }
    }

    public long increment(String key) {
        try {
            Long value = redis.opsForValue().increment(key);
            return value == null ? 0L : value;
        } catch (RuntimeException ex) {
            log.warn("Redis increment skipped for key {}: {}", key, ex.getMessage());
            return 0L;
        }
    }

    public <T> T get(String key, Class<T> type) {
        return read(key, json -> objectMapper.readValue(json, type));
    }

    public <T> T get(String key, JavaType type) {
        return read(key, json -> objectMapper.readValue(json, type));
    }

    public void put(String key, Object value, Duration ttl) {
        try {
            redis.opsForValue().set(key, objectMapper.writeValueAsString(value), ttl);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Redis cache serialization failed: " + key, ex);
        } catch (RuntimeException ex) {
            log.warn("Redis write skipped for key {}: {}", key, ex.getMessage());
        }
    }

    public void delete(String key) {
        try {
            redis.delete(key);
        } catch (RuntimeException ex) {
            log.warn("Redis delete skipped for key {}: {}", key, ex.getMessage());
        }
    }

    private <T> T read(String key, JsonReader<T> reader) {
        String json = getRaw(key);
        if (json == null) return null;
        try {
            return reader.read(json);
        } catch (Exception ex) {
            // A malformed cache entry must not make the business API unavailable.
            delete(key);
            return null;
        }
    }

    @FunctionalInterface
    private interface JsonReader<T> {
        T read(String json) throws Exception;
    }
}
