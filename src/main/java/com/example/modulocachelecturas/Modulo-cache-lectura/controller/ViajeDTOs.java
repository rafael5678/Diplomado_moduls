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
 * CONTRATOS DE DATOS (DTOs) DE LA API DE CACHÉ Y LECTURAS.
 * 
 * Los DTOs (Data Transfer Objects) aíslan el modelo interno de la base de datos
 * del JSON público que viaja por la red hacia los clientes o hacia otros módulos.
 */
public class ViajeDTOs {

    // =========================================================================
    // 1. DTO DE ASIENTO INDIVIDUAL
    // =========================================================================
    @Data               // Lombok: Autogenera Getters, Setters, toString y equals
    @Builder            // Lombok: Permite construir objetos con patrón Builder (.builder().build())
    @NoArgsConstructor  // Constructor vacío requerido para deserializar desde JSON
    @AllArgsConstructor // Constructor con todos los atributos
    public static class SillaDTO implements Serializable {
        private static final long serialVersionUID = 1L;

        private Long id;            // ID único de la silla en la base de datos
        private Integer numeroSilla;// Número visible del asiento (1 al 40)
        private Integer piso;       // Nivel del autobús (Piso 1 o Piso 2)
        private String ubicacion;   // Distribución física: "VENTANA" o "PASILLO"
        private EstadoSilla estado; // Estado actual: DISPONIBLE, RESERVADA u OCUPADA
        private BigDecimal precio;  // Precio monetario exacto del asiento
    }

    // =========================================================================
    // 2. DTO PRINCIPAL: MAPA INTERACTIVO DE SILLAS (Objeto guardado en Redis)
    // =========================================================================
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MapaSillasDTO implements Serializable {
        private static final long serialVersionUID = 1L;

        private Long viajeId;          // Identificador del viaje
        private String codigoViaje;    // Código de despacho (ej: "VIAJE-2026-001")
        private String rutaCodigo;     // Código de la ruta (ej: "RUTA-BOG-MED")
        private String origen;         // Ciudad o terminal de salida
        private String destino;        // Ciudad o terminal de llegada

        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate fechaSalida; // Fecha del viaje en formato legible (YYYY-MM-DD)

        @JsonFormat(pattern = "HH:mm:ss")
        private LocalTime horaSalida;  // Hora exacta de salida (HH:mm:ss)

        private String busPlaca;       // Placa del autobús asignado
        private int totalSillas;       // Capacidad máxima de asientos (ej: 40)
        private int sillasDisponibles; // Conteo en tiempo real de sillas libres
        private int sillasReservadas;  // Conteo de sillas en proceso de pago
        private int sillasOcupadas;    // Conteo de sillas vendidas definitivamente
        private List<SillaDTO> sillas; // Colección ordenada de los 40 asientos

        // Metadatos técnicos de auditoría del ASR 1:
        private String source;         // Origen del dato: "CACHE_REDIS (Hit)" o "DATABASE_POSTGRES (Miss)"
        private Long responseTimeMs;   // Latencia real de respuesta en milisegundos

        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime cachedAt;// Fecha y hora exacta en la que se guardó en memoria RAM
    }

    // =========================================================================
    // 3. DTO PARA BÚSQUEDA DE VIAJES PROGRAMADOS DISPONIBLES
    // =========================================================================
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ViajeDisponibleDTO implements Serializable {
        private static final long serialVersionUID = 1L;

        private Long viajeId;          // Identificador numérico del viaje
        private String codigoViaje;    // Código identificador operativo
        private String origen;         // Ciudad de partida
        private String destino;        // Ciudad de llegada

        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate fechaSalida; // Día programado del viaje

        @JsonFormat(pattern = "HH:mm:ss")
        private LocalTime horaSalida;  // Horario de salida

        private String busPlaca;       // Vehículo asignado
        private BigDecimal precioBase; // Tarifa mínima del pasaje
        private int sillasDisponibles; // Cantidad de puestos libres aún a la venta
        private int capacidadTotal;    // Puestos totales del bus
        private String estado;         // Estado del viaje (PROGRAMADO, EN_CURSO)
        private String source;         // De dónde provino la consulta (CACHE o DB)
    }

    // =========================================================================
    // 4. PAYLOAD PARA INVALIDACIÓN DE CACHÉ POR EVENTO
    // =========================================================================
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InvalidateCacheRequest implements Serializable {
        private static final long serialVersionUID = 1L;

        private Long viajeId;  // ID del viaje cuyo mapa en Redis debe borrarse de inmediato
        private String motivo; // Causa del evento (ej: "RESERVA_CONFIRMADA", "PAGO_APROBADO")
    }

    // =========================================================================
    // 5. DTO DE TELEMETRÍA Y OBSERVABILIDAD (Validación del ASR 1)
    // =========================================================================
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CacheMetricsDTO implements Serializable {
        private static final long serialVersionUID = 1L;

        private long totalRequests;            // Total acumulado de lecturas recibidas
        private long cacheHits;                // Peticiones resueltas en memoria RAM (ultra-rápidas)
        private long cacheMisses;              // Peticiones que tuvieron que consultar PostgreSQL
        private double hitRatioPercentage;     // Porcentaje de efectividad (% aciertos en memoria)
        private double dbReadsSavedPercentage; // Carga de lectura ahorrada a la base de datos
        private long cacheInvalidations;       // Veces que se limpió la caché por eventos de venta
        private long mutexLockAcquisitions;    // Bloqueos adquiridos para proteger la BD
        private long mutexLockWaits;           // Peticiones concurrentes protegidas del colapso
        private boolean asr1PerformanceTargetMet;// Bandera booleana: true si el ahorro supera el 85%
    }
}
