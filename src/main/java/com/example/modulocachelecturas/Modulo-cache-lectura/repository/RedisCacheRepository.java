package com.example.modulocachelecturas.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Repository
@RequiredArgsConstructor
public class RedisCacheRepository implements CacheRepository {

    private final RedisTemplate<String, Object> redisTemplate;
    private final DefaultRedisScript<Long> unlockScript;
    private final ObjectMapper redisObjectMapper;

    @Override
    public <T> Optional<T> get(String key, Class<T> targetClass) {
        try {
            Object rawValue = redisTemplate.opsForValue().get(key);
            if (rawValue == null) {
                return Optional.empty();
            }
            if (targetClass.isInstance(rawValue)) {
                return Optional.of(targetClass.cast(rawValue));
            }
            T converted = redisObjectMapper.convertValue(rawValue, targetClass);
            return Optional.ofNullable(converted);
        } catch (Exception e) {
            log.error("[CacheRepository] Error al obtener clave {} de Redis: {}", key, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void set(String key, Object value, long ttlSeconds) {
        try {
            redisTemplate.opsForValue().set(key, value, Duration.ofSeconds(ttlSeconds));
            log.debug("[CacheRepository] Clave '{}' guardada con TTL de {} segundos", key, ttlSeconds);
        } catch (Exception e) {
            log.error("[CacheRepository] Error al guardar clave {} en Redis: {}", key, e.getMessage());
        }
    }

    @Override
    public boolean delete(String key) {
        try {
            Boolean deleted = redisTemplate.delete(key);
            log.debug("[CacheRepository] Clave '{}' eliminada: {}", key, deleted);
            return Boolean.TRUE.equals(deleted);
        } catch (Exception e) {
            log.error("[CacheRepository] Error al eliminar clave {} de Redis: {}", key, e.getMessage());
            return false;
        }
    }

    @Override
    public void deleteByPattern(String pattern) {
        try {
            Set<String> keys = redisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.info("[CacheRepository] Se eliminaron {} claves que coincidian con el patron '{}'", keys.size(), pattern);
            }
        } catch (Exception e) {
            log.error("[CacheRepository] Error al eliminar patron {} de Redis: {}", pattern, e.getMessage());
        }
    }

    @Override
    public boolean exists(String key) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        } catch (Exception e) {
            log.error("[CacheRepository] Error al verificar existencia de clave {} en Redis: {}", key, e.getMessage());
            return false;
        }
    }

    @Override
    public boolean acquireLock(String lockKey, String lockValue, long expireMillis) {
        try {
            Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
                    lockKey,
                    lockValue,
                    Duration.ofMillis(expireMillis)
            );
            return Boolean.TRUE.equals(acquired);
        } catch (Exception e) {
            log.error("[CacheRepository] Error al intentar adquirir Mutex Lock {}: {}", lockKey, e.getMessage());
            return false;
        }
    }

    @Override
    public boolean releaseLock(String lockKey, String lockValue) {
        try {
            Long result = redisTemplate.execute(
                    unlockScript,
                    Collections.singletonList(lockKey),
                    lockValue
            );
            return result != null && result == 1L;
        } catch (Exception e) {
            log.error("[CacheRepository] Error al liberar Mutex Lock {}: {}", lockKey, e.getMessage());
            return false;
        }
    }
}
