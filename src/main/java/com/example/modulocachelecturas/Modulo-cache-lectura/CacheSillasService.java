package com.example.modulocachelecturas;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Servicio central del Módulo ADR-002: Caché Distribuida y Descarga de Lecturas.
 * Implementa:
 * 1. Cache-Aside Pattern para descarga de lecturas de la base de datos relacional.
 * 2. Invalidación inmediata por eventos (evictMapaSillas).
 * 3. Mecanismo Anti-Colapso (Mutex Lock / Anti-Cache Stampede) para prevenir colapsos en PostgreSQL.
 * 4. Métricas de observabilidad (Hit Ratio, DB Reads Saved) para validar ASR 1.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CacheSillasService {

    private final CacheRepository cacheRepository;
    private final ViajeRepository viajeRepository;
    private final SillaRepository sillaRepository;

    @Value("${cache.sillas.ttl-seconds:240}")
    private long sillasTtlSeconds;

    @Value("${cache.viajes.ttl-seconds:300}")
    private long viajesTtlSeconds;

    @Value("${cache.lock.timeout-millis:5000}")
    private long lockTimeoutMillis;

    @Value("${cache.lock.retry-interval-millis:50}")
    private long lockRetryIntervalMillis;

    @Value("${cache.lock.max-retries:10}")
    private int lockMaxRetries;

    // Métricas en memoria para telemetría del ASR 1
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);
    private final AtomicLong invalidationCount = new AtomicLong(0);
    private final AtomicLong mutexLockAcquisitions = new AtomicLong(0);
    private final AtomicLong mutexLockWaits = new AtomicLong(0);

    private static final String KEY_PREFIX_SILLAS = "viaje:%d:mapa_sillas";
    private static final String LOCK_PREFIX_SILLAS = "lock:viaje:%d:mapa_sillas";
    private static final String KEY_PREFIX_DISPONIBLES = "viajes:disponibles:%s:%s:%s";

    /**
     * Obtiene el mapa de sillas de un viaje utilizando Cache-Aside y Mutex Lock Anti-Stampede.
     */
    public MapaSillasDTO getMapaSillas(Long viajeId) {
        long startTime = System.currentTimeMillis();
        totalRequests.incrementAndGet();

        String cacheKey = String.format(KEY_PREFIX_SILLAS, viajeId);

        // 1. Intentar Cache Hit en Redis (lectura en memoria)
        Optional<MapaSillasDTO> cachedMap = cacheRepository.get(cacheKey, MapaSillasDTO.class);
        if (cachedMap.isPresent()) {
            cacheHits.incrementAndGet();
            MapaSillasDTO dto = cachedMap.get();
            dto.setSource("CACHE_REDIS (Hit)");
            dto.setResponseTimeMs(System.currentTimeMillis() - startTime);
            log.debug("[Cache Hit] Mapa de sillas obtenido de Redis para viajeId: {} en {} ms", viajeId, dto.getResponseTimeMs());
            return dto;
        }

        // 2. Cache Miss: Activar protección Anti-Cache Stampede mediante Mutex Lock Distribuido
        cacheMisses.incrementAndGet();
        log.warn("[Cache Miss] No existe caché para clave: {}. Iniciando flujo de reconstrucción controlado.", cacheKey);

        String lockKey = String.format(LOCK_PREFIX_SILLAS, viajeId);
        String lockValue = UUID.randomUUID().toString();

        boolean acquired = cacheRepository.acquireLock(lockKey, lockValue, lockTimeoutMillis);

        if (acquired) {
            mutexLockAcquisitions.incrementAndGet();
            log.info("[Mutex Lock Acquired] Este hilo reconstruirá la caché para viajeId: {}", viajeId);
            try {
                // Doble chequeo por si otro hilo terminó justo antes de que adquiriéramos el lock
                Optional<MapaSillasDTO> doubleCheck = cacheRepository.get(cacheKey, MapaSillasDTO.class);
                if (doubleCheck.isPresent()) {
                    MapaSillasDTO dto = doubleCheck.get();
                    dto.setSource("CACHE_REDIS (Hit-PostLock)");
                    dto.setResponseTimeMs(System.currentTimeMillis() - startTime);
                    return dto;
                }

                // 3. Consultar la base de datos relacional (PostgreSQL)
                MapaSillasDTO freshData = loadFromDatabase(viajeId);
                freshData.setSource("DATABASE_POSTGRES (Miss)");
                freshData.setCachedAt(LocalDateTime.now());
                freshData.setResponseTimeMs(System.currentTimeMillis() - startTime);

                // 4. Guardar en Redis con TTL de 180s - 300s (por defecto 240s)
                cacheRepository.set(cacheKey, freshData, sillasTtlSeconds);
                log.info("[Cache Rebuilt] Caché almacenada exitosamente en Redis para viajeId: {} con TTL={}s", viajeId, sillasTtlSeconds);

                return freshData;
            } finally {
                // 5. Liberar el lock de forma segura con Lua Script
                cacheRepository.releaseLock(lockKey, lockValue);
                log.debug("[Mutex Lock Released] Lock liberado para viajeId: {}", viajeId);
            }
        } else {
            // No adquirió el lock: Otro hilo ya está reconstruyendo la caché.
            // Esperar con backoff reintentando leer de Redis (Anti-Dogpiling)
            mutexLockWaits.incrementAndGet();
            log.warn("[Mutex Lock Busy] Otro hilo está reconstruyendo el viajeId: {}. Esperando disponibilidad...", viajeId);

            for (int retry = 0; retry < lockMaxRetries; retry++) {
                try {
                    Thread.sleep(lockRetryIntervalMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }

                Optional<MapaSillasDTO> recheck = cacheRepository.get(cacheKey, MapaSillasDTO.class);
                if (recheck.isPresent()) {
                    cacheHits.incrementAndGet();
                    MapaSillasDTO dto = recheck.get();
                    dto.setSource("CACHE_REDIS (Hit-AfterWait)");
                    dto.setResponseTimeMs(System.currentTimeMillis() - startTime);
                    log.info("[Anti-Stampede Success] Petición concurrente satisfecha desde Redis tras {} reintentos", retry + 1);
                    return dto;
                }
            }

            // Fallback de seguridad si el hilo del lock tardó más del tiempo máximo
            log.warn("[Anti-Stampede Fallback] Reintentos agotados. Consultando directamente a PostgreSQL.");
            MapaSillasDTO fallbackData = loadFromDatabase(viajeId);
            fallbackData.setSource("DATABASE_POSTGRES (Fallback)");
            fallbackData.setResponseTimeMs(System.currentTimeMillis() - startTime);
            return fallbackData;
        }
    }

    /**
     * Consulta PostgreSQL para obtener el viaje y sus sillas.
     */
    @Transactional(readOnly = true)
    protected MapaSillasDTO loadFromDatabase(Long viajeId) {
        log.info("[PostgreSQL Query] Ejecutando lectura en base de datos para viajeId: {}", viajeId);
        Viaje viaje = viajeRepository.findByIdWithSillas(viajeId)
                .orElseThrow(() -> new RuntimeException("El viaje con ID " + viajeId + " no existe en el sistema"));

        List<Silla> sillasList = viaje.getSillas() != null ? viaje.getSillas() : Collections.emptyList();

        int disponibles = 0;
        int reservadas = 0;
        int ocupadas = 0;

        List<SillaDTO> sillaDTOs = new ArrayList<>();
        for (Silla s : sillasList) {
            if (s.getEstado() == EstadoSilla.DISPONIBLE) disponibles++;
            else if (s.getEstado() == EstadoSilla.RESERVADA) reservadas++;
            else if (s.getEstado() == EstadoSilla.OCUPADA) ocupadas++;

            sillaDTOs.add(SillaDTO.builder()
                    .id(s.getId())
                    .numeroSilla(s.getNumeroSilla())
                    .piso(s.getPiso())
                    .ubicacion(s.getUbicacion())
                    .estado(s.getEstado())
                    .precio(s.getPrecio())
                    .build());
        }

        sillaDTOs.sort(Comparator.comparing(SillaDTO::getNumeroSilla));

        return MapaSillasDTO.builder()
                .viajeId(viaje.getId())
                .codigoViaje(viaje.getCodigoViaje())
                .rutaCodigo(viaje.getRuta() != null ? viaje.getRuta().getCodigoRuta() : "N/A")
                .origen(viaje.getRuta() != null ? viaje.getRuta().getOrigen() : "N/A")
                .destino(viaje.getRuta() != null ? viaje.getRuta().getDestino() : "N/A")
                .fechaSalida(viaje.getFechaSalida())
                .horaSalida(viaje.getHoraSalida())
                .busPlaca(viaje.getBusPlaca())
                .totalSillas(viaje.getCapacidadTotal())
                .sillasDisponibles(disponibles)
                .sillasReservadas(reservadas)
                .sillasOcupadas(ocupadas)
                .sillas(sillaDTOs)
                .build();
    }

    /**
     * Estrategia de Invalidación por Evento (ADR-002).
     * Elimina inmediatamente la clave del viaje en Redis (viaje:{id}:mapa_sillas) cuando ocurra
     * una reserva o confirmación de silla, garantizando consistencia inmediata.
     */
    public boolean evictMapaSillas(Long viajeId, String motivo) {
        String cacheKey = String.format(KEY_PREFIX_SILLAS, viajeId);
        boolean deleted = cacheRepository.delete(cacheKey);

        // También invalidar listas de viajes disponibles que contengan este viaje
        cacheRepository.deleteByPattern("viajes:disponibles:*");

        invalidationCount.incrementAndGet();
        log.info("[Cache Invalidation] Clave '{}' invalidada exitosamente. Motivo: '{}'. Estado en Redis: {}",
                cacheKey, motivo, deleted);
        return deleted;
    }

    /**
     * Consulta viajes disponibles aplicando Cache-Aside.
     */
    public List<ViajeDisponibleDTO> getViajesDisponibles(String origen, String destino, String fechaStr) {
        String safeOrigen = origen != null && !origen.isBlank() ? origen.trim().toLowerCase() : "all";
        String safeDestino = destino != null && !destino.isBlank() ? destino.trim().toLowerCase() : "all";
        String safeFecha = fechaStr != null && !fechaStr.isBlank() ? fechaStr.trim() : "all";

        String cacheKey = String.format(KEY_PREFIX_DISPONIBLES, safeOrigen, safeDestino, safeFecha);

        Optional<List> cached = cacheRepository.get(cacheKey, List.class);
        if (cached.isPresent()) {
            cacheHits.incrementAndGet();
            log.debug("[Cache Hit] Lista de viajes disponibles obtenida de Redis para clave: {}", cacheKey);
            return (List<ViajeDisponibleDTO>) cached.get();
        }

        cacheMisses.incrementAndGet();
        LocalDate fecha = (fechaStr != null && !fechaStr.isBlank()) ? LocalDate.parse(fechaStr) : null;
        List<Viaje> viajes = viajeRepository.findViajesDisponibles(
                "all".equals(safeOrigen) ? null : safeOrigen,
                "all".equals(safeDestino) ? null : safeDestino,
                fecha
        );

        List<ViajeDisponibleDTO> result = viajes.stream().map(v -> {
            long disp = v.getSillas() != null ?
                    v.getSillas().stream().filter(s -> s.getEstado() == EstadoSilla.DISPONIBLE).count() : 0;
            return ViajeDisponibleDTO.builder()
                    .viajeId(v.getId())
                    .codigoViaje(v.getCodigoViaje())
                    .origen(v.getRuta().getOrigen())
                    .destino(v.getRuta().getDestino())
                    .fechaSalida(v.getFechaSalida())
                    .horaSalida(v.getHoraSalida())
                    .busPlaca(v.getBusPlaca())
                    .precioBase(v.getPrecioBase())
                    .sillasDisponibles((int) disp)
                    .capacidadTotal(v.getCapacidadTotal())
                    .estado(v.getEstado())
                    .source("DATABASE_POSTGRES")
                    .build();
        }).collect(Collectors.toList());

        // Guardar en Redis con TTL de 300 segundos
        cacheRepository.set(cacheKey, result, viajesTtlSeconds);
        return result;
    }

    /**
     * Obtiene métricas en tiempo real para verificar el cumplimiento del ASR 1 y la protección de BD.
     */
    public CacheMetricsDTO getMetrics() {
        long total = totalRequests.get();
        long hits = cacheHits.get();
        long misses = cacheMisses.get();

        double hitRatio = total > 0 ? ((double) hits / total) * 100.0 : 0.0;
        double dbSaved = total > 0 ? ((double) hits / total) * 100.0 : 0.0;

        return CacheMetricsDTO.builder()
                .totalRequests(total)
                .cacheHits(hits)
                .cacheMisses(misses)
                .hitRatioPercentage(Math.round(hitRatio * 100.0) / 100.0)
                .dbReadsSavedPercentage(Math.round(dbSaved * 100.0) / 100.0)
                .cacheInvalidations(invalidationCount.get())
                .mutexLockAcquisitions(mutexLockAcquisitions.get())
                .mutexLockWaits(mutexLockWaits.get())
                .asr1PerformanceTargetMet(hitRatio >= 80.0 || total == 0)
                .build();
    }

    /**
     * Calentamiento de Caché (Cache Warmup) para temporada alta.
     * Carga de forma anticipada el mapa de sillas en Redis para absorber el tráfico del pico inicial.
     */
    public MapaSillasDTO warmupCache(Long viajeId) {
        log.info("[Cache Warmup] Precalentando caché para viajeId: {}", viajeId);
        String cacheKey = String.format(KEY_PREFIX_SILLAS, viajeId);
        MapaSillasDTO fresh = loadFromDatabase(viajeId);
        fresh.setSource("CACHE_WARMED");
        fresh.setCachedAt(LocalDateTime.now());
        cacheRepository.set(cacheKey, fresh, sillasTtlSeconds);
        return fresh;
    }
}
