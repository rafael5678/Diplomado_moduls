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
 * Se conecta con Redis en el puerto 6379 para ejecutar operaciones atómicas
 * en memoria RAM (lectura, escritura con TTL y cerrojos distribuidos).
 */
@Slf4j                          // Habilita logging (mensajes de depuración y error)
@Repository                     // Indica a Spring que es un componente de acceso a datos
@RequiredArgsConstructor        // Genera el constructor para inyectar los 3 atributos 'final'
public class RedisCacheRepository implements CacheRepository {

    private final RedisTemplate<String, Object> redisTemplate; // Plantilla de Spring para interactuar con Redis
    private final DefaultRedisScript<Long> unlockScript;        // Script Lua precompilado para liberar el Mutex Lock
    private final ObjectMapper redisObjectMapper;              // Conversor de JSON a objetos Java con fechas compatibles

    /**
     * OPERACIÓN GET: Lee una clave desde Redis RAM y la convierte al tipo DTO esperado.
     */
    @Override
    public <T> Optional<T> get(String key, Class<T> targetClass) {
        try {
            Object raw = redisTemplate.opsForValue().get(key); // Comando nativo GET de Redis
            if (raw == null) return Optional.empty();          // Cache Miss: no existe o expiró su TTL
            if (targetClass.isInstance(raw)) return Optional.of(targetClass.cast(raw));
            // Convierte el JSON recibido de Redis al objeto DTO tipado
            return Optional.ofNullable(redisObjectMapper.convertValue(raw, targetClass));
        } catch (Exception e) {
            // Si Redis se desconecta, no revienta la app: hace fallback y retorna vacío para que consulte la BD
            log.warn("[Cache] Fallback por error al leer clave {}: {}", key, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * OPERACIÓN SET: Guarda un objeto en Redis con tiempo de expiración (TTL en segundos).
     * Equivale al comando nativo SETEX de Redis.
     */
    @Override
    public void set(String key, Object value, long ttlSeconds) {
        try {
            redisTemplate.opsForValue().set(key, value, Duration.ofSeconds(ttlSeconds));
        } catch (Exception e) {
            log.error("[Cache] Error al escribir clave {}: {}", key, e.getMessage());
        }
    }

    /**
     * OPERACIÓN DELETE: Elimina inmediatamente una clave de Redis (Comando DEL).
     */
    @Override
    public boolean delete(String key) {
        Boolean res = redisTemplate.delete(key);
        return Boolean.TRUE.equals(res);
    }

    /**
     * OPERACIÓN DELETE POR PATRÓN: Borra todas las claves que coincidan con un comodín (*).
     * Ejemplo: "viajes:disponibles:*" borra todas las búsquedas guardadas.
     */
    @Override
    public void deleteByPattern(String pattern) {
        Set<String> keys = redisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    /**
     * OPERACIÓN EXISTS: Verifica en microsegundos si una clave existe en memoria RAM.
     */
    @Override
    public boolean exists(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    /**
     * ADQUISICIÓN DE MUTEX LOCK DISTRIBUIDO (Anti-Cache Stampede).
     * Ejecuta atómicamente: SET lockKey lockValue NX PX expireMillis
     * - NX (setIfAbsent): Solo guarda si la clave NO existía previamente.
     * - PX (Duration.ofMillis): Expira a los 5 segundos para evitar bloqueos perpetuos (deadlocks).
     * Solo el PRIMER hilo en llegar recibe 'true'; los demás reciben 'false'.
     */
    @Override
    public boolean acquireLock(String lockKey, String lockValue, long expireMillis) {
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, lockValue, Duration.ofMillis(expireMillis));
        return Boolean.TRUE.equals(acquired);
    }

    /**
     * LIBERACIÓN SEGURA DEL MUTEX LOCK.
     * Ejecuta el script Lua atómico para validar que el token coincida con el dueño antes de borrar.
     */
    @Override
    public boolean releaseLock(String lockKey, String lockValue) {
        Long res = redisTemplate.execute(unlockScript, Collections.singletonList(lockKey), lockValue);
        return res != null && res == 1L; // Retorna true si borró exitosamente
    }
}
