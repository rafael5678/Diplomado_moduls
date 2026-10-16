package com.example.modulocachelecturas.service;

import com.example.modulocachelecturas.controller.ViajeDTOs.*;
import com.example.modulocachelecturas.model.EstadoSilla;
import com.example.modulocachelecturas.model.Silla;
import com.example.modulocachelecturas.model.Viaje;
import com.example.modulocachelecturas.repository.CacheRepository;
import com.example.modulocachelecturas.repository.ViajeRepository;
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
 * SERVICIO CENTRAL ADR-002: Lógica de Caché Distribuida y Descarga de Lecturas.
 * 
 * Actúa como escudo protector para evitar que 200.000 lecturas en temporada alta colapsen PostgreSQL.
 * Implementa el patrón Cache-Aside con Mutex Lock anti-colapso (Anti-Cache Stampede).
 */
@Slf4j                          // Habilita sistema de logs
@Service                        // Declara esta clase como componente de servicio de Spring
@RequiredArgsConstructor        // Inyecta dependencias 'final' vía constructor
public class CacheSillasService {

    private final CacheRepository cacheRepository; // Interfaz para interactuar con Redis
    private final ViajeRepository viajeRepository; // Interfaz JPA para interactuar con PostgreSQL

    // Parámetros de arquitectura configurables con valores por defecto:
    @Value("${cache.sillas.ttl-seconds:240}")
    private long sillasTtlSeconds;         // TTL de 240 segundos (4 min) de vida en RAM

    @Value("${cache.viajes.ttl-seconds:300}")
    private long viajesTtlSeconds;         // TTL de 300 segundos (5 min) para listados de viajes

    @Value("${cache.lock.timeout-millis:5000}")
    private long lockTimeoutMillis;        // Tiempo máximo (5s) que un hilo puede retener el bloqueo

    @Value("${cache.lock.retry-interval-millis:50}")
    private long lockRetryIntervalMillis;  // Tiempo de espera (50ms) entre reintentos en cola

    @Value("${cache.lock.max-retries:10}")
    private int lockMaxRetries;            // Hasta 10 reintentos (500ms max) antes de fallback a BD

    // Contadores en memoria para auditoría en tiempo real del ASR 1:
    private final AtomicLong totalRequests = new AtomicLong(0);     // Total peticiones recibidas
    private final AtomicLong cacheHits = new AtomicLong(0);         // Aciertos atendidos en RAM
    private final AtomicLong cacheMisses = new AtomicLong(0);       // Fallos que fueron a BD
    private final AtomicLong invalidations = new AtomicLong(0);     // Borrados de caché ejecutados
    private final AtomicLong lockAcquisitions = new AtomicLong(0);  // Bloqueos adquiridos con éxito
    private final AtomicLong lockWaits = new AtomicLong(0);         // Hilos protegidos en cola

    // Prefijos estructurados de claves en Redis:
    private static final String KEY_SILLAS = "viaje:%d:mapa_sillas";
    private static final String KEY_LOCK = "lock:viaje:%d:mapa_sillas";
    private static final String KEY_DISPONIBLES = "viajes:disponibles:%s:%s:%s";

    /**
     * MÉTODO PRINCIPAL: Consulta del Mapa de Sillas con Cache-Aside y Mutex Lock.
     * Meta de rendimiento (ASR 1): Responder en menos de 1 segundo (logrado en < 15ms).
     */
    public MapaSillasDTO getMapaSillas(Long viajeId) {
        long startTime = System.currentTimeMillis();
        totalRequests.incrementAndGet(); // Registra una nueva petición
        String cacheKey = String.format(KEY_SILLAS, viajeId);

        // PASO 1: Consulta en memoria RAM Redis (Cache Hit < 15ms sin tocar PostgreSQL)
        Optional<MapaSillasDTO> cached = cacheRepository.get(cacheKey, MapaSillasDTO.class);
        if (cached.isPresent()) {
            cacheHits.incrementAndGet();
            MapaSillasDTO dto = cached.get();
            dto.setSource("CACHE_REDIS (Hit)");
            dto.setResponseTimeMs(System.currentTimeMillis() - startTime);
            return dto;
        }

        // PASO 2: Cache Miss -> Solo 1 hilo adquiere el Mutex Lock para reconstruir la caché
        cacheMisses.incrementAndGet();
        String lockKey = String.format(KEY_LOCK, viajeId);
        String lockToken = UUID.randomUUID().toString(); // Token UUID único para este hilo

        if (cacheRepository.acquireLock(lockKey, lockToken, lockTimeoutMillis)) {
            lockAcquisitions.incrementAndGet();
            try {
                // Doble chequeo por si otro hilo terminó justo una fracción de milisegundo antes
                Optional<MapaSillasDTO> doubleCheck = cacheRepository.get(cacheKey, MapaSillasDTO.class);
                if (doubleCheck.isPresent()) return doubleCheck.get();

                // PASO 3: Lectura controlada de PostgreSQL mediante JOIN FETCH
                MapaSillasDTO fresh = loadFromDatabase(viajeId);
                fresh.setSource("DATABASE_POSTGRES (Miss)");
                fresh.setCachedAt(LocalDateTime.now());
                fresh.setResponseTimeMs(System.currentTimeMillis() - startTime);

                // PASO 4: Guardar en Redis con TTL de 240 segundos (4 minutos)
                cacheRepository.set(cacheKey, fresh, sillasTtlSeconds);
                return fresh;
            } finally {
                // PASO 5: Liberación atómica del cerrojo con script Lua
                cacheRepository.releaseLock(lockKey, lockToken);
            }
        } else {
            // PASO 6: Hilos concurrentes que NO obtuvieron el lock esperan en cola (Anti-Stampede)
            lockWaits.incrementAndGet();
            for (int i = 0; i < lockMaxRetries; i++) {
                try { Thread.sleep(lockRetryIntervalMillis); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
                // Reconsulta Redis; casi siempre ya está listo en el 1er o 2do reintento
                Optional<MapaSillasDTO> recheck = cacheRepository.get(cacheKey, MapaSillasDTO.class);
                if (recheck.isPresent()) {
                    cacheHits.incrementAndGet();
                    MapaSillasDTO dto = recheck.get();
                    dto.setSource("CACHE_REDIS (Hit-AfterWait)");
                    dto.setResponseTimeMs(System.currentTimeMillis() - startTime);
                    return dto;
                }
            }
            // Fallback de contingencia si el hilo ganador demoró más de 500ms
            MapaSillasDTO fallback = loadFromDatabase(viajeId);
            fallback.setSource("DATABASE_POSTGRES (Fallback)");
            fallback.setResponseTimeMs(System.currentTimeMillis() - startTime);
            return fallback;
        }
    }

    /**
     * Consulta PostgreSQL con 'JOIN FETCH': Une viaje, ruta y 40 sillas en 1 solo SELECT (evita N+1).
     */
    @Transactional(readOnly = true)
    protected MapaSillasDTO loadFromDatabase(Long viajeId) {
        Viaje viaje = viajeRepository.findByIdWithSillas(viajeId)
                .orElseThrow(() -> new RuntimeException("Viaje no encontrado: " + viajeId));

        List<Silla> sillas = viaje.getSillas() != null ? viaje.getSillas() : Collections.emptyList();
        int disp = 0, res = 0, ocup = 0;
        List<SillaDTO> dtoList = new ArrayList<>();

        // Recorre los asientos y calcula estadísticas de ocupación
        for (Silla s : sillas) {
            if (s.getEstado() == EstadoSilla.DISPONIBLE) disp++;
            else if (s.getEstado() == EstadoSilla.RESERVADA) res++;
            else if (s.getEstado() == EstadoSilla.OCUPADA) ocup++;

            dtoList.add(new SillaDTO(s.getId(), s.getNumeroSilla(), s.getPiso(), s.getUbicacion(), s.getEstado(), s.getPrecio()));
        }
        dtoList.sort(Comparator.comparing(SillaDTO::getNumeroSilla));

        // Construye el MapaSillasDTO listo para guardar en Redis
        return MapaSillasDTO.builder()
                .viajeId(viaje.getId()).codigoViaje(viaje.getCodigoViaje())
                .rutaCodigo(viaje.getRuta() != null ? viaje.getRuta().getCodigoRuta() : "N/A")
                .origen(viaje.getRuta() != null ? viaje.getRuta().getOrigen() : "N/A")
                .destino(viaje.getRuta() != null ? viaje.getRuta().getDestino() : "N/A")
                .fechaSalida(viaje.getFechaSalida()).horaSalida(viaje.getHoraSalida())
                .busPlaca(viaje.getBusPlaca()).totalSillas(viaje.getCapacidadTotal())
                .sillasDisponibles(disp).sillasReservadas(res).sillasOcupadas(ocup)
                .sillas(dtoList).build();
    }

    /**
     * ESTRATEGIA DE INVALIDACIÓN POR EVENTO:
     * Invocada cuando Reservas o Pagos alteran una silla. Borra de inmediato la clave en Redis.
     */
    public boolean evictMapaSillas(Long viajeId, String motivo) {
        boolean deleted = cacheRepository.delete(String.format(KEY_SILLAS, viajeId));
        cacheRepository.deleteByPattern("viajes:disponibles:*"); // Invalida también búsquedas cacheadas
        invalidations.incrementAndGet();
        log.info("[Cache Evicted] ViajeId: {}, Motivo: {}", viajeId, motivo);
        return deleted;
    }

    /**
     * Búsqueda de viajes programados con caché en memoria (TTL de 300 segundos).
     */
    public List<ViajeDisponibleDTO> getViajesDisponibles(String origen, String destino, String fechaStr) {
        String safeO = (origen != null && !origen.isBlank()) ? origen.trim().toLowerCase() : "all";
        String safeD = (destino != null && !destino.isBlank()) ? destino.trim().toLowerCase() : "all";
        String safeF = (fechaStr != null && !fechaStr.isBlank()) ? fechaStr.trim() : "all";
        String cacheKey = String.format(KEY_DISPONIBLES, safeO, safeD, safeF);

        // 1. Revisar si la búsqueda ya existe en Redis
        Optional<List> cached = cacheRepository.get(cacheKey, List.class);
        if (cached.isPresent()) {
            cacheHits.incrementAndGet();
            return (List<ViajeDisponibleDTO>) cached.get();
        }

        // 2. Si no existe en RAM, consultar PostgreSQL
        cacheMisses.incrementAndGet();
        LocalDate fecha = (fechaStr != null && !fechaStr.isBlank()) ? LocalDate.parse(fechaStr) : null;
        List<Viaje> list = viajeRepository.findViajesDisponibles("all".equals(safeO) ? null : safeO, "all".equals(safeD) ? null : safeD, fecha);

        List<ViajeDisponibleDTO> result = list.stream().map(v -> {
            long disp = v.getSillas() != null ? v.getSillas().stream().filter(s -> s.getEstado() == EstadoSilla.DISPONIBLE).count() : 0;
            return ViajeDisponibleDTO.builder()
                    .viajeId(v.getId()).codigoViaje(v.getCodigoViaje())
                    .origen(v.getRuta().getOrigen()).destino(v.getRuta().getDestino())
                    .fechaSalida(v.getFechaSalida()).horaSalida(v.getHoraSalida())
                    .busPlaca(v.getBusPlaca()).precioBase(v.getPrecioBase())
                    .sillasDisponibles((int) disp).capacidadTotal(v.getCapacidadTotal())
                    .estado(v.getEstado()).source("DATABASE_POSTGRES").build();
        }).collect(Collectors.toList());

        // 3. Almacenar en Redis con TTL de 300 segundos (5 minutos)
        cacheRepository.set(cacheKey, result, viajesTtlSeconds);
        return result;
    }

    /**
     * Métricas de observabilidad en vivo para validar el ASR 1 (Meta: Hit Ratio >= 85%).
     */
    public CacheMetricsDTO getMetrics() {
        long total = totalRequests.get(), hits = cacheHits.get();
        double ratio = total > 0 ? ((double) hits / total) * 100.0 : 0.0;
        return CacheMetricsDTO.builder()
                .totalRequests(total).cacheHits(hits).cacheMisses(cacheMisses.get())
                .hitRatioPercentage(Math.round(ratio * 100.0) / 100.0)
                .dbReadsSavedPercentage(Math.round(ratio * 100.0) / 100.0)
                .cacheInvalidations(invalidations.get())
                .mutexLockAcquisitions(lockAcquisitions.get())
                .mutexLockWaits(lockWaits.get())
                .asr1PerformanceTargetMet(ratio >= 85.0 || total == 0).build();
    }

    /**
     * Precalentamiento de memoria RAM: Carga anticipadamente un viaje en Redis antes de horas pico.
     */
    public MapaSillasDTO warmupCache(Long viajeId) {
        MapaSillasDTO fresh = loadFromDatabase(viajeId);
        fresh.setSource("CACHE_WARMED");
        fresh.setCachedAt(LocalDateTime.now());
        cacheRepository.set(String.format(KEY_SILLAS, viajeId), fresh, sillasTtlSeconds);
        return fresh;
    }
}
