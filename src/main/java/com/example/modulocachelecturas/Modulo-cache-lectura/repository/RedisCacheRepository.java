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

/**
 * IMPLEMENTACIÓN DEL REPOSITORIO DE CACHÉ CON REDIS.
 *
 * Utiliza RedisTemplate para comunicarse con Redis en el puerto 6379.
 * Contiene operaciones clave-valor de alta velocidad y mecanismos de bloqueo atómico.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class RedisCacheRepository implements CacheRepository {

    private final RedisTemplate<String, Object> redisTemplate;
    private final DefaultRedisScript<Long> unlockScript;
    private final ObjectMapper redisObjectMapper;

    /**
     * Obtiene un valor desde Redis y lo convierte de forma segura al tipo DTO esperado.
     */
    @Override
    public <T> Optional<T> get(String key, Class<T> targetClass) {
        try {
            Object rawValue = redisTemplate.opsForValue().get(key);
            if (rawValue == null) {
                return Optional.empty(); // Clave no existe o ya expiró por TTL
            }
            if (targetClass.isInstance(rawValue)) {
                return Optional.of(targetClass.cast(rawValue));
            }
            // Si Redis devolvió un LinkedHashMap genérico, lo transformamos al objeto DTO tipado
            T converted = redisObjectMapper.convertValue(rawValue, targetClass);
            return Optional.ofNullable(converted);
        } catch (Exception e) {
            log.error("[CacheRepository] Error al leer la clave '{}' de Redis: {}", key, e.getMessage());
            return Optional.empty(); // En caso de fallo en Redis, retornamos vacío para fallback a BD
        }
    }

    /**
     * Guarda un valor en memoria con expiración automática (TTL en segundos).
     * Ejecuta el comando nativo SETEX de Redis.
     */
    @Override
    public void set(String key, Object value, long ttlSeconds) {
        try {
            redisTemplate.opsForValue().set(key, value, Duration.ofSeconds(ttlSeconds));
            log.debug("[CacheRepository] Clave '{}' almacenada con TTL={}s", key, ttlSeconds);
        } catch (Exception e) {
            log.error("[CacheRepository] Error al escribir clave '{}' en Redis: {}", key, e.getMessage());
        }
    }

    /**
     * Elimina una clave específica de Redis (Comando DEL).
     */
    @Override
    public boolean delete(String key) {
        try {
            Boolean deleted = redisTemplate.delete(key);
            return Boolean.TRUE.equals(deleted);
        } catch (Exception e) {
            log.error("[CacheRepository] Error al borrar clave '{}' en Redis: {}", key, e.getMessage());
            return false;
        }
    }

    /**
     * Elimina múltiples claves que coincidan con un comodín (ej: 'viajes:disponibles:*').
     */
    @Override
    public void deleteByPattern(String pattern) {
        try {
            Set<String> keys = redisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.info("[CacheRepository] Se eliminaron {} claves bajo el patrón '{}'", keys.size(), pattern);
            }
        } catch (Exception e) {
            log.error("[CacheRepository] Error al purgar patrón '{}': {}", pattern, e.getMessage());
        }
    }

    /**
     * Verifica rápidamente si una clave existe en memoria (Comando EXISTS).
     */
    @Override
    public boolean exists(String key) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        } catch (Exception e) {
            log.error("[CacheRepository] Error verificando existencia de clave '{}': {}", key, e.getMessage());
            return false;
        }
    }

    /**
     * ADQUISICIÓN DE MUTEX LOCK DISTRIBUIDO (Anti-Cache Stampede).
     *
     * Ejecuta atómicamente: SET lockKey lockValue NX PX expireMillis
     * - NX: 'Not eXists' -> Solo crea la clave si NO existía previamente.
     * - PX: Establece la expiración en milisegundos para evitar bloqueos perpetuos (deadlocks).
     *
     * Retorna true si este hilo fue el primero y obtuvo el permiso exclusivo.
     */
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
            log.error("[CacheRepository] Error al adquirir cerrojo Mutex '{}': {}", lockKey, e.getMessage());
            return false;
        }
    }

    /**
     * LIBERACIÓN SEGURA DEL MUTEX LOCK.
     *
     * Envía el script Lua precompilado a Redis para que se evalúe de manera atómica:
     * Si el valor coincide con el token UUID del hilo, borra la clave; de lo contrario no hace nada.
     */
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
            log.error("[CacheRepository] Error al liberar cerrojo Mutex '{}': {}", lockKey, e.getMessage());
            return false;
        }
    }
}
