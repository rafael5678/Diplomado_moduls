package com.example.modulocachelecturas.controller;

import com.example.modulocachelecturas.model.EstadoSilla;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * CONTRATOS DE DATOS (DTOs) CONSOLIDADOS PARA LA API DE CACHÉ Y LECTURAS.
 *
 * Agrupa las estructuras de transferencia de datos en un solo archivo para evitar
 * dispersión de clases y optimizar el tamaño del módulo.
 */
public class ViajeDTOs {

    /**
     * DTO ligero para representar una silla en la vista interactiva.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SillaDTO implements Serializable {
        private static final long serialVersionUID = 1L;

        private Long id;
        private Integer numeroSilla;
        private Integer piso;
        private String ubicacion; // "VENTANA" o "PASILLO"
        private EstadoSilla estado; // DISPONIBLE, RESERVADA, OCUPADA
        private BigDecimal precio;
    }

    /**
     * DTO principal del Mapa de Sillas (ADR-002):
     * Es el objeto que se serializa y se guarda en memoria Redis.
     * Contiene metadatos de auditoría: 'source' (procedencia del dato) y 'responseTimeMs'.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MapaSillasDTO implements Serializable {
        private static final long serialVersionUID = 1L;

        private Long viajeId;
        private String codigoViaje;
        private String rutaCodigo;
        private String origen;
        private String destino;

        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate fechaSalida;

        @JsonFormat(pattern = "HH:mm:ss")
        private LocalTime horaSalida;

        private String busPlaca;
        private int totalSillas;
        private int sillasDisponibles;
        private int sillasReservadas;
        private int sillasOcupadas;
        private List<SillaDTO> sillas;

        private String source; // "CACHE_REDIS (Hit)" o "DATABASE_POSTGRES (Miss)"
        private Long responseTimeMs; // Tiempo de respuesta en milisegundos

        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime cachedAt;
    }

    /**
     * DTO para el listado de viajes programados disponibles en el sistema.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ViajeDisponibleDTO implements Serializable {
        private static final long serialVersionUID = 1L;

        private Long viajeId;
        private String codigoViaje;
        private String origen;
        private String destino;

        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate fechaSalida;

        @JsonFormat(pattern = "HH:mm:ss")
        private LocalTime horaSalida;

        private String busPlaca;
        private BigDecimal precioBase;
        private int sillasDisponibles;
        private int capacidadTotal;
        private String estado;
        private String source;
    }

    /**
     * Payload para solicitud de invalidación de caché por evento.
     * Enviado por los módulos de Reservas o Pagos.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class InvalidateCacheRequest implements Serializable {
        private static final long serialVersionUID = 1L;

        private Long viajeId; // ID del viaje cuya caché de sillas debe borrarse
        private String motivo;  // Motivo descriptivo (ej: "RESERVA_CONFIRMADA", "PAGO_EXITOSO")
    }

    /**
     * DTO de Observabilidad y Métricas en tiempo real (Validación del ASR 1):
     * Expone indicadores clave de rendimiento (KPIs de arquitectura).
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CacheMetricsDTO implements Serializable {
        private static final long serialVersionUID = 1L;

        private long totalRequests; // Total de peticiones recibidas
        private long cacheHits; // Peticiones resueltas en memoria RAM
        private long cacheMisses; // Peticiones que requirieron ir a PostgreSQL
        private double hitRatioPercentage; // Porcentaje de efectividad de caché
        private double dbReadsSavedPercentage; // Carga de lectura ahorrada a la BD (meta > 85-90%)
        private long cacheInvalidations; // Cantidad de invalidaciones ejecutadas
        private long mutexLockAcquisitions; // Bloqueos adquiridos exitosamente
        private long mutexLockWaits; // Hilos concurrentes protegidos de colapsar la BD
        private boolean asr1PerformanceTargetMet; // Indica si se cumple el SLA de rendimiento
    }
}
