package com.example.modulocachelecturas.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * CONFIGURACIÓN DE SERIALIZACIÓN JSON Y SCRIPTS LUA PARA REDIS.
 */
@Configuration // Clase de configuración de Spring Boot proveedora de Beans
public class RedisConfig {

    /**
     * BEAN 1: Mapeador JSON Jackson compatible con fechas Java 8/17/21 (LocalDate, LocalTime).
     */
    @Bean
    public ObjectMapper redisObjectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule()) // Permite serializar tipos como LocalDate y LocalTime
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS) // Guarda fechas legibles ("YYYY-MM-DD")
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false); // Tolerante a cambios de campos
    }

    /**
     * BEAN 2: Plantilla principal para interactuar con Redis (RedisTemplate).
     * Configura claves como texto String y valores como documentos JSON estructurados.
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory, ObjectMapper mapper) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory); // Conexión provista automáticamente por Spring Boot Lettuce
        template.setKeySerializer(new StringRedisSerializer()); // Claves en texto plano (ej: "viaje:1:mapa_sillas")
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer(mapper)); // Valores en JSON
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer(mapper));
        return template;
    }

    /**
     * BEAN 3: Script Lua precompilado para liberación atómica del cerrojo Mutex Lock.
     * Solo borra la clave si el token guardado coincide con el token del hilo dueño.
     */
    @Bean
    public DefaultRedisScript<Long> unlockScript() {
        String script = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";
        return new DefaultRedisScript<>(script, Long.class);
    }
}
