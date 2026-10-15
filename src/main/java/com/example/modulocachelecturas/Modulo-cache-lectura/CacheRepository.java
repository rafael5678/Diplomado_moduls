package com.example.modulocachelecturas;

import java.util.Optional;

/**
 * Abstracción del repositorio de caché distribuida (Redis).
 * Permite desacoplar la lógica de negocio de la implementación directa del cliente de Redis.
 * Cumple con los requisitos de ADR-002: comandos GET, SETEX, DEL, EXISTS y Bloqueo Distribuido (Mutex).
 */
public interface CacheRepository {

    /**
     * Obtiene un valor deserializado de la caché por su clave.
     */
    <T> Optional<T> get(String key, Class<T> targetClass);

    /**
     * Almacena un valor en la caché con un tiempo de vida (TTL) en segundos (comando SETEX).
     */
    void set(String key, Object value, long ttlSeconds);

    /**
     * Elimina una clave de la caché (comando DEL).
     */
    boolean delete(String key);

    /**
     * Elimina todas las claves que coincidan con un patrón determinado.
     */
    void deleteByPattern(String pattern);

    /**
     * Verifica si una clave existe en la caché (comando EXISTS).
     */
    boolean exists(String key);

    /**
     * Intenta adquirir un bloqueo distribuido (Mutex) con expiración para prevenir Cache Stampede.
     * Equivalente a SET lockKey lockValue NX PX expireMillis.
     */
    boolean acquireLock(String lockKey, String lockValue, long expireMillis);

    /**
     * Libera de forma atómica el bloqueo distribuido solo si el lockValue coincide.
     */
    boolean releaseLock(String lockKey, String lockValue);
}
