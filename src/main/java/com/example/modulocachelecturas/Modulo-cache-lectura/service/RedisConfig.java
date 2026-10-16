package com.example.modulocachelecturas.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * CONFIGURACIÓN DE RESILIENCIA Y SERIALIZACIÓN PARA REDIS.
 *
 * Responsabilidad:
 * 1. Establecer conexión asíncrona y no bloqueante con el motor Redis (cliente Lettuce).
 * 2. Aplicar límites de timeout para evitar que hilos queden colgados si la red falla.
 * 3. Serializar objetos DTO a formato JSON con soporte nativo de fechas Java 8/17/21.
 * 4. Proveer un Script Lua atómico para liberar cerrojos de concurrencia de forma segura.
 */
@Configuration
public class RedisConfig {

    // Inyección de parámetros de conexión definidos en application.properties o variables de entorno
    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.timeout:2000ms}")
    private Duration redisTimeout;

    /**
     * Fábrica de conexiones Lettuce con resiliencia empresarial.
     * Si Redis se desconecta momentáneamente, se reintenta automáticamente sin reiniciar la app.
     */
    @Bean
    public LettuceConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration serverConfig = new RedisStandaloneConfiguration(redisHost, redisPort);

        // Opciones de socket: Tiempo máximo para establecer socket (2s) y mantener conexión viva (keepAlive)
        SocketOptions socketOptions = SocketOptions.builder()
                .connectTimeout(Duration.ofMillis(2000))
                .keepAlive(true)
                .build();

        // Opciones de cliente: Reconexión automática activada para resistir caídas momentáneas de red
        ClientOptions clientOptions = ClientOptions.builder()
                .socketOptions(socketOptions)
                .autoReconnect(true)
                .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
                .build();

        // Configuración del cliente unificada con el tiempo límite de lectura/escritura (redisTimeout)
        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                .clientOptions(clientOptions)
                .commandTimeout(redisTimeout)
                .build();

        LettuceConnectionFactory factory = new LettuceConnectionFactory(serverConfig, clientConfig);
        factory.setValidateConnection(true); // Valida la conexión al inicializar el contenedor
        return factory;
    }

    /**
     * Mapeador Jackson personalizado para manejar tipos modernos de fecha (LocalDate, LocalTime).
     * Evita que las fechas se guarden como arreglos numéricos extraños en Redis.
     */
    @Bean
    public ObjectMapper redisObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule()); // Habilita soporte para LocalDate y LocalDateTime
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS); // Formato legible "YYYY-MM-DD"
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false); // Tolerancia a cambios de campos
        return mapper;
    }

    /**
     * Plantilla principal de Redis (RedisTemplate):
     * Configura serializadores para que las claves se guarden como texto legible (String)
     * y los valores como documentos JSON estructurados.
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory, ObjectMapper redisObjectMapper) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Claves legibles en formato texto (ej: "viaje:1:mapa_sillas")
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        // Valores como JSON compatible con el modelo
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer(redisObjectMapper);

        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }

    /**
     * SCRIPT LUA PARA LIBERACIÓN ATÓMICA DEL MUTEX LOCK (Patrón Redlock).
     *
     * Problema que soluciona:
     * Si el Hilo A tarda mucho y su lock expira, el Hilo B adquiere un nuevo lock.
     * Si luego el Hilo A intenta borrar la clave sin verificar, borraría el lock del Hilo B.
     *
     * Solución:
     * Este script valida primero si el token UUID que guardó el hilo coincide con el actual.
     * Solo si coincide, borra la clave. Todo ocurre en una sola operación atómica dentro de Redis.
     */
    @Bean
    public DefaultRedisScript<Long> unlockScript() {
        String script = "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                        "    return redis.call('del', KEYS[1]) " +
                        "else " +
                        "    return 0 " +
                        "end";
        return new DefaultRedisScript<>(script, Long.class);
    }
}
