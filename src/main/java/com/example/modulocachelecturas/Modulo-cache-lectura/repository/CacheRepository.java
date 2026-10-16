package com.example.modulocachelecturas.repository;

import java.util.Optional;

/**
 * Abstracción del repositorio de caché distribuida (Redis).
 * ADR-002: Comandos GET, SETEX, DEL, EXISTS y Bloqueo Distribuido (Mutex).
 */
public interface CacheRepository {

    <T> Optional<T> get(String key, Class<T> targetClass);

    void set(String key, Object value, long ttlSeconds);

    boolean delete(String key);

    void deleteByPattern(String pattern);

    boolean exists(String key);

    boolean acquireLock(String lockKey, String lockValue, long expireMillis);

    boolean releaseLock(String lockKey, String lockValue);
}
