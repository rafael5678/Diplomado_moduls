package com.example.modulocachelecturas.service;

import com.example.modulocachelecturas.controller.ViajeDTOs.*;
import com.example.modulocachelecturas.model.EstadoSilla;
import com.example.modulocachelecturas.model.Silla;
import com.example.modulocachelecturas.model.Viaje;
import com.example.modulocachelecturas.repository.CacheRepository;
import com.example.modulocachelecturas.repository.SillaRepository;
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
 * SERVICIO CENTRAL DEL MÓDULO ADR-002: Caché Distribuida y Descarga de Lecturas.
 *
 * Responsabilidad en la Arquitectura:
 * Actúa como la primera línea de defensa para proteger la base de datos PostgreSQL.
 * Implementa el patrón Cache-Aside y un Mutex Lock Distribuido para evitar que miles
 * de usuarios concurrentes en temporada alta colapsen la base de datos al expirar la caché.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CacheSillasService {

    // Inyección de dependencias necesarias para interactuar con Redis y PostgreSQL
    private final CacheRepository cacheRepository;
    private final ViajeRepository viajeRepository;
    private final SillaRepository sillaRepository;

    // Configuración de tiempos de vida (TTL) y resiliencia leídos desde application.properties
    @Value("${cache.sillas.ttl-seconds:240}")
    private long sillasTtlSeconds; // TTL de 240 segundos (4 min) para el mapa de sillas

    @Value("${cache.viajes.ttl-seconds:300}")
    private long viajesTtlSeconds; // TTL de 300 segundos (5 min) para listados de viajes

    @Value("${cache.lock.timeout-millis:5000}")
    private long lockTimeoutMillis; // Tiempo máximo (5s) que un hilo puede retener el bloqueo

    @Value("${cache.lock.retry-interval-millis:50}")
    private long lockRetryIntervalMillis; // Tiempo de espera (50ms) entre reintentos de lectura

    @Value("${cache.lock.max-retries:10}")
    private int lockMaxRetries; // Hasta 10 reintentos (500ms en total) antes de ir a BD de emergencia

    // Contadores atómicos en memoria para medir en tiempo real el cumplimiento del ASR 1
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);
    private final AtomicLong invalidationCount = new AtomicLong(0);
    private final AtomicLong mutexLockAcquisitions = new AtomicLong(0);
    private final AtomicLong mutexLockWaits = new AtomicLong(0);

    // Formato estructurado de claves en Redis para mantener orden y aislamiento
    private static final String KEY_PREFIX_SILLAS = "viaje:%d:mapa_sillas";
    private static final String LOCK_PREFIX_SILLAS = "lock:viaje:%d:mapa_sillas";
    private static final String KEY_PREFIX_DISPONIBLES = "viajes:disponibles:%s:%s:%s";

    /**
     * Consulta del Mapa Interactivo de Sillas mediante Cache-Aside con Mutex Lock.
     * Meta: Responder en menos de 1 segundo (ASR 1) absorbiendo las peticiones en memoria.
     */
    public MapaSillasDTO getMapaSillas(Long viajeId) {
        long startTime = System.currentTimeMillis();
        totalRequests.incrementAndGet(); // Registra la llegada de una nueva petición

        String cacheKey = String.format(KEY_PREFIX_SILLAS, viajeId);

        // PASO 1: Consulta inicial a Redis (Memoria RAM ultra-rápida)
        // Si el dato está en memoria (Cache Hit), se retorna inmediatamente sin tocar PostgreSQL.
        Optional<MapaSillasDTO> cachedMap = cacheRepository.get(cacheKey, MapaSillasDTO.class);
        if (cachedMap.isPresent()) {
            cacheHits.incrementAndGet(); // Registramos el acierto en caché
            MapaSillasDTO dto = cachedMap.get();
            dto.setSource("CACHE_REDIS (Hit)");
            dto.setResponseTimeMs(System.currentTimeMillis() - startTime);
            log.debug("[Cache Hit] Mapa de sillas obtenido de Redis para viajeId: {} en {} ms", viajeId, dto.getResponseTimeMs());
            return dto;
        }

        // PASO 2: Manejo de Cache Miss con Protección Anti-Cache Stampede (Dogpiling)
        // Si 5.000 usuarios consultan a la vez una clave expirada, solo UNO debe consultar la BD.
        cacheMisses.incrementAndGet();
        log.warn("[Cache Miss] Clave no encontrada en Redis: {}. Iniciando bloqueo distribuido.", cacheKey);

        String lockKey = String.format(LOCK_PREFIX_SILLAS, viajeId);
        String lockValue = UUID.randomUUID().toString(); // Identificador único del hilo dueño del lock

        // Intentamos adquirir el Mutex Lock en Redis (operación atómica SET NX PX)
        boolean acquired = cacheRepository.acquireLock(lockKey, lockValue, lockTimeoutMillis);

        if (acquired) {
            // Este hilo ganó el derecho de consultar PostgreSQL y reconstruir la memoria
            mutexLockAcquisitions.incrementAndGet();
            log.info("[Mutex Lock Acquired] Hilo autorizado para reconstruir caché de viajeId: {}", viajeId);
            try {
                // Doble chequeo de seguridad (Double-Checked Locking Pattern):
                // Verifica si otro hilo terminó justo una fracción de milisegundo antes
                Optional<MapaSillasDTO> doubleCheck = cacheRepository.get(cacheKey, MapaSillasDTO.class);
                if (doubleCheck.isPresent()) {
                    MapaSillasDTO dto = doubleCheck.get();
                    dto.setSource("CACHE_REDIS (Hit-PostLock)");
                    dto.setResponseTimeMs(System.currentTimeMillis() - startTime);
                    return dto;
                }

                // PASO 3: Lectura controlada de la base de datos relacional (PostgreSQL)
                MapaSillasDTO freshData = loadFromDatabase(viajeId);
                freshData.setSource("DATABASE_POSTGRES (Miss)");
                freshData.setCachedAt(LocalDateTime.now());
                freshData.setResponseTimeMs(System.currentTimeMillis() - startTime);

                // PASO 4: Almacenar en Redis con TTL de 240 segundos (4 minutos)
                cacheRepository.set(cacheKey, freshData, sillasTtlSeconds);
                log.info("[Cache Rebuilt] Caché actualizada en Redis para viajeId: {} con TTL={}s", viajeId, sillasTtlSeconds);

                return freshData;
            } finally {
                // PASO 5: Liberación atómica y segura del bloqueo usando Script Lua
                // Garantiza que solo el hilo que creó el bloqueo pueda borrarlo
                cacheRepository.releaseLock(lockKey, lockValue);
            }
        } else {
            // PASO 6: Hilos concurrentes que NO obtuvieron el lock.
            // En vez de ir a colapsar PostgreSQL, esperan unos milisegundos a que el hilo ganador termine.
            mutexLockWaits.incrementAndGet();
            log.warn("[Mutex Lock Busy] Otro hilo reconstruye viajeId: {}. Esperando en cola...", viajeId);

            for (int retry = 0; retry < lockMaxRetries; retry++) {
                try {
                    Thread.sleep(lockRetryIntervalMillis); // Pausa breve de 50ms
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }

                // Vuelve a consultar Redis; normalmente en el reintento 1 o 2 ya está disponible
                Optional<MapaSillasDTO> recheck = cacheRepository.get(cacheKey, MapaSillasDTO.class);
                if (recheck.isPresent()) {
                    cacheHits.incrementAndGet();
                    MapaSillasDTO dto = recheck.get();
                    dto.setSource("CACHE_REDIS (Hit-AfterWait)");
                    dto.setResponseTimeMs(System.currentTimeMillis() - startTime);
                    log.info("[Anti-Stampede Success] Petición atendida desde Redis tras {} reintentos", retry + 1);
                    return dto;
                }
            }

            // Fallback de contingencia: Si el hilo ganador demoró demasiado, se consulta la BD directamente
            log.warn("[Anti-Stampede Fallback] Reintentos agotados. Consultando directamente a PostgreSQL.");
            MapaSillasDTO fallbackData = loadFromDatabase(viajeId);
            fallbackData.setSource("DATABASE_POSTGRES (Fallback)");
            fallbackData.setResponseTimeMs(System.currentTimeMillis() - startTime);
            return fallbackData;
        }
    }

    /**
     * Consulta directa a PostgreSQL para armar el mapa de sillas cuando ocurre un Cache Miss.
     * Usa transacción de solo lectura para máxima eficiencia.
     */
    @Transactional(readOnly = true)
    protected MapaSillasDTO loadFromDatabase(Long viajeId) {
        log.info("[PostgreSQL Query] Consultando tablas 'viajes', 'rutas' y 'sillas' para viajeId: {}", viajeId);
        Viaje viaje = viajeRepository.findByIdWithSillas(viajeId)
                .orElseThrow(() -> new RuntimeException("El viaje con ID " + viajeId + " no existe en el sistema"));

        List<Silla> sillasList = viaje.getSillas() != null ? viaje.getSillas() : Collections.emptyList();

        // Conteo del estado de ocupación de las sillas
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

        // Ordenamos las sillas por número ascendente (1, 2, 3... 40) para la interfaz de usuario
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
     * ESTRATEGIA DE INVALIDACIÓN POR EVENTO (ADR-002).
     * Es invocada cuando un usuario reserva o paga una silla (desde otros módulos).
     * Elimina de forma inmediata la clave en Redis para evitar que otros usuarios vean
     * la silla como disponible (garantía de consistencia inmediata de lecturas).
     */
    public boolean evictMapaSillas(Long viajeId, String motivo) {
        String cacheKey = String.format(KEY_PREFIX_SILLAS, viajeId);
        boolean deleted = cacheRepository.delete(cacheKey);

        // También invalidamos listados de búsqueda que contengan datos de este viaje
        cacheRepository.deleteByPattern("viajes:disponibles:*");

        invalidationCount.incrementAndGet();
        log.info("[Cache Invalidation] Clave '{}' eliminada de Redis. Motivo del evento: '{}'", cacheKey, motivo);
        return deleted;
    }

    /**
     * Búsqueda de viajes disponibles con Cache-Aside.
     * Clave dinámica en Redis según origen, destino y fecha.
     */
    public List<ViajeDisponibleDTO> getViajesDisponibles(String origen, String destino, String fechaStr) {
        // Normalización de parámetros para evitar duplicidad de claves en Redis
        String safeOrigen = origen != null && !origen.isBlank() ? origen.trim().toLowerCase() : "all";
        String safeDestino = destino != null && !destino.isBlank() ? destino.trim().toLowerCase() : "all";
        String safeFecha = fechaStr != null && !fechaStr.isBlank() ? fechaStr.trim() : "all";

        String cacheKey = String.format(KEY_PREFIX_DISPONIBLES, safeOrigen, safeDestino, safeFecha);

        // 1. Revisar si la búsqueda ya existe en memoria
        Optional<List> cached = cacheRepository.get(cacheKey, List.class);
        if (cached.isPresent()) {
            cacheHits.incrementAndGet();
            return (List<ViajeDisponibleDTO>) cached.get();
        }

        // 2. Si no existe, consultar PostgreSQL
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

        // 3. Guardar en Redis con TTL de 300 segundos (5 minutos)
        cacheRepository.set(cacheKey, result, viajesTtlSeconds);
        return result;
    }

    /**
     * Métricas de observabilidad para auditar el cumplimiento del ASR 1.
     * Permite verificar en tiempo real si se cumple el >85% de reducción de carga a la BD.
     */
    public CacheMetricsDTO getMetrics() {
        long total = totalRequests.get();
        long hits = cacheHits.get();
        long misses = cacheMisses.get();

        // Cálculo de porcentaje de aciertos en memoria vs consultas a base de datos
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
                .asr1PerformanceTargetMet(hitRatio >= 85.0 || total == 0) // Valida meta del 85%
                .build();
    }

    /**
     * Calentamiento proactivo de caché (Cache Pre-warming).
     * Pre-carga el mapa en Redis antes de que inicien las horas pico de venta de tiquetes.
     */
    public MapaSillasDTO warmupCache(Long viajeId) {
        log.info("[Cache Warmup] Precalentando memoria para viajeId: {}", viajeId);
        String cacheKey = String.format(KEY_PREFIX_SILLAS, viajeId);
        MapaSillasDTO fresh = loadFromDatabase(viajeId);
        fresh.setSource("CACHE_WARMED");
        fresh.setCachedAt(LocalDateTime.now());
        cacheRepository.set(cacheKey, fresh, sillasTtlSeconds);
        return fresh;
    }
}
