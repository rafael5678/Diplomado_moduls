package com.example.modulocachelecturas.controller;

import com.example.modulocachelecturas.controller.ViajeDTOs.*;
import com.example.modulocachelecturas.service.CacheSillasService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CONTROLADOR REST - MÓDULO ADR-002 (Caché Distribuida y Descarga de Lecturas).
 *
 * Expone la API pública del módulo hacia clientes móviles, web y otros módulos
 * del monolito modular (Reservas, Pagos, Tarifas).
 */
@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ViajeController {

    // Inyección del servicio de caché y lecturas
    private final CacheSillasService cacheSillasService;

    /**
     * ENDPOINT PRINCIPAL: Consulta del Mapa Interactivo de Sillas.
     * Ruta: GET /api/v1/viajes/{id}/sillas
     *
     * Comportamiento:
     * - Consulta Redis en milisegundos.
     * - Retorna cabecera 'X-Cache: HIT' si provino de memoria RAM (sin tocar BD).
     * - Retorna cabecera 'X-Cache: MISS' si requirió consulta a PostgreSQL.
     * - Retorna 'X-Response-Time-Ms' indicando la latencia exacta de respuesta (meta < 1.0s).
     */
    @GetMapping("/viajes/{id}/sillas")
    public ResponseEntity<MapaSillasDTO> getMapaSillas(@PathVariable("id") Long id) {
        log.info("[HTTP GET] Solicitud de mapa de sillas recibida para viaje ID: {}", id);
        MapaSillasDTO mapa = cacheSillasService.getMapaSillas(id);

        // Agregamos cabeceras HTTP para auditoría técnica de rendimiento y caché
        HttpHeaders headers = new HttpHeaders();
        boolean isHit = mapa.getSource() != null && mapa.getSource().contains("CACHE");
        headers.add("X-Cache", isHit ? "HIT" : "MISS");
        headers.add("X-Response-Time-Ms", String.valueOf(mapa.getResponseTimeMs()));

        return new ResponseEntity<>(mapa, headers, HttpStatus.OK);
    }

    /**
     * Consulta de viajes programados disponibles según criterios de búsqueda.
     * Ruta: GET /api/v1/viajes/disponibles?origen=Bogota&destino=Medellin&fecha=2026-12-01
     */
    @GetMapping("/viajes/disponibles")
    public ResponseEntity<List<ViajeDisponibleDTO>> getViajesDisponibles(
            @RequestParam(value = "origen", required = false) String origen,
            @RequestParam(value = "destino", required = false) String destino,
            @RequestParam(value = "fecha", required = false) String fecha) {
        log.info("[HTTP GET] Consulta de viajes disponibles (origen={}, destino={}, fecha={})", origen, destino, fecha);
        List<ViajeDisponibleDTO> disponibles = cacheSillasService.getViajesDisponibles(origen, destino, fecha);
        return ResponseEntity.ok(disponibles);
    }

    /**
     * ENDPOINT DE INVALIDACIÓN POR EVENTO:
     * Ruta: POST /api/v1/cache/invalidate
     *
     * Utilidad en el Monolito Modular:
     * Invocado de inmediato cuando el módulo de Reservas o Pagos confirma o libera una silla.
     * Borra la clave en Redis para que el próximo usuario vea el estado actualizado en tiempo real.
     */
    @PostMapping("/cache/invalidate")
    public ResponseEntity<Map<String, Object>> invalidateCache(@RequestBody InvalidateCacheRequest request) {
        log.info("[HTTP POST] Evento de invalidación recibido para viajeId: {}, motivo: {}",
                request.getViajeId(), request.getMotivo());

        boolean evicted = cacheSillasService.evictMapaSillas(request.getViajeId(), request.getMotivo());

        // Respuesta estructurada con confirmación de la clave removida
        Map<String, Object> response = new HashMap<>();
        response.put("status", "SUCCESS");
        response.put("viajeId", request.getViajeId());
        response.put("motivo", request.getMotivo());
        response.put("keyEvicted", String.format("viaje:%d:mapa_sillas", request.getViajeId()));
        response.put("cacheCleaned", evicted);
        response.put("timestamp", System.currentTimeMillis());

        return ResponseEntity.ok(response);
    }

    /**
     * ENDPOINT DE OBSERVABILIDAD Y TELEMETRÍA (ASR 1):
     * Ruta: GET /api/v1/cache/metrics
     *
     * Demuestra y audita que más del 85-90% de las lecturas son absorbidas por la caché
     * y no llegan a sobrecargar la base de datos PostgreSQL.
     */
    @GetMapping("/cache/metrics")
    public ResponseEntity<CacheMetricsDTO> getCacheMetrics() {
        CacheMetricsDTO metrics = cacheSillasService.getMetrics();
        return ResponseEntity.ok(metrics);
    }

    /**
     * PRECALENTAMIENTO DE CACHÉ (Cache Warmup):
     * Ruta: POST /api/v1/cache/warmup/{viajeId}
     *
     * Usado antes de abrir ventas de temporada alta para cargar los mapas en Redis
     * de forma proactiva, absorbiendo el primer impacto masivo de usuarios.
     */
    @PostMapping("/cache/warmup/{viajeId}")
    public ResponseEntity<MapaSillasDTO> warmupCache(@PathVariable("viajeId") Long viajeId) {
        log.info("[HTTP POST] Ejecutando Warmup de memoria para viajeId: {}", viajeId);
        MapaSillasDTO mapa = cacheSillasService.warmupCache(viajeId);
        return ResponseEntity.ok(mapa);
    }
}
