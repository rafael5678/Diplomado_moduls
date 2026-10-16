package com.example.modulocachelecturas.repository;

import java.util.Optional;

/**
 * INTERFAZ DE ABSTRACCIÓN DEL REPOSITORIO DE CACHÉ DISTRIBUIDA.
 *
 * Desacopla la lógica del servicio de la tecnología concreta de caché (Redis).
 * Permite cumplir los principios SOLID (Inversión de Dependencias).
 */
public interface CacheRepository {

    /**
     * Consulta un objeto deserializado en memoria a partir de su clave.
     */
    <T> Optional<T> get(String key, Class<T> targetClass);

    /**
     * Almacena un objeto con un tiempo de vida (TTL) en segundos (comando SETEX).
     */
    void set(String key, Object value, long ttlSeconds);

    /**
     * Elimina una clave de forma inmediata (comando DEL).
     */
    boolean delete(String key);

    /**
     * Elimina un conjunto de claves coincidentes con un patrón comodín.
     */
    void deleteByPattern(String pattern);

    /**
     * Verifica si una clave existe actualmente en memoria (comando EXISTS).
     */
    boolean exists(String key);

    /**
     * Adquiere un cerrojo distribuido (Mutex) con expiración para evitar el colapso por Cache Stampede.
     */
    boolean acquireLock(String lockKey, String lockValue, long expireMillis);

    /**
     * Libera el cerrojo distribuido de forma atómica validando el token de propiedad.
     */
    boolean releaseLock(String lockKey, String lockValue);
}
