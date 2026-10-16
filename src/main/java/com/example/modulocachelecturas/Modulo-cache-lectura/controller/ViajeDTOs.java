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
 * Agrupación unificada de DTOs para el módulo de Caché y Lectura (ADR-002).
 * Reduce la dispersión de archivos y centraliza los contratos de datos de la API.
 */
public class ViajeDTOs {

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
        private String ubicacion;
        private EstadoSilla estado;
        private BigDecimal precio;
    }

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
        private Long responseTimeMs;

        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime cachedAt;
    }

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

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class InvalidateCacheRequest implements Serializable {
        private static final long serialVersionUID = 1L;

        private Long viajeId;
        private String motivo;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CacheMetricsDTO implements Serializable {
        private static final long serialVersionUID = 1L;

        private long totalRequests;
        private long cacheHits;
        private long cacheMisses;
        private double hitRatioPercentage;
        private double dbReadsSavedPercentage;
        private long cacheInvalidations;
        private long mutexLockAcquisitions;
        private long mutexLockWaits;
        private boolean asr1PerformanceTargetMet;
    }
}
