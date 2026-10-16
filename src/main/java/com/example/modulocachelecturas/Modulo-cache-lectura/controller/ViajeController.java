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
 * Controlador REST del Módulo ADR-002: Caché Distribuida y Descarga de Lecturas.
 * Expone:
 * - GET  /api/v1/viajes/{id}/sillas     : Mapa interactivo de sillas con Cache-Aside y Mutex Lock.
 * - GET  /api/v1/viajes/disponibles     : Búsqueda de viajes y rutas disponibles.
 * - POST /api/v1/cache/invalidate       : Invalidación inmediata de caché por evento de reserva.
 * - GET  /api/v1/cache/metrics          : Observabilidad y validación de ASR 1 (Hit Ratio y DB load).
 * - POST /api/v1/cache/warmup/{viajeId} : Calentamiento proactivo de caché.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ViajeController {

    private final CacheSillasService cacheSillasService;

    @GetMapping("/viajes/{id}/sillas")
    public ResponseEntity<MapaSillasDTO> getMapaSillas(@PathVariable("id") Long id) {
        log.info("[HTTP GET] Solicitud de mapa de sillas para viaje ID: {}", id);
        MapaSillasDTO mapa = cacheSillasService.getMapaSillas(id);

        HttpHeaders headers = new HttpHeaders();
        boolean isHit = mapa.getSource() != null && mapa.getSource().contains("CACHE");
        headers.add("X-Cache", isHit ? "HIT" : "MISS");
        headers.add("X-Response-Time-Ms", String.valueOf(mapa.getResponseTimeMs()));

        return new ResponseEntity<>(mapa, headers, HttpStatus.OK);
    }

    @GetMapping("/viajes/disponibles")
    public ResponseEntity<List<ViajeDisponibleDTO>> getViajesDisponibles(
            @RequestParam(value = "origen", required = false) String origen,
            @RequestParam(value = "destino", required = false) String destino,
            @RequestParam(value = "fecha", required = false) String fecha) {
        log.info("[HTTP GET] Solicitud de viajes disponibles (origen={}, destino={}, fecha={})", origen, destino, fecha);
        List<ViajeDisponibleDTO> disponibles = cacheSillasService.getViajesDisponibles(origen, destino, fecha);
        return ResponseEntity.ok(disponibles);
    }

    @PostMapping("/cache/invalidate")
    public ResponseEntity<Map<String, Object>> invalidateCache(@RequestBody InvalidateCacheRequest request) {
        log.info("[HTTP POST] Invalidación de caché solicitada para viajeId: {}, motivo: {}",
                request.getViajeId(), request.getMotivo());

        boolean evicted = cacheSillasService.evictMapaSillas(request.getViajeId(), request.getMotivo());

        Map<String, Object> response = new HashMap<>();
        response.put("status", "SUCCESS");
        response.put("viajeId", request.getViajeId());
        response.put("motivo", request.getMotivo());
        response.put("keyEvicted", String.format("viaje:%d:mapa_sillas", request.getViajeId()));
        response.put("cacheCleaned", evicted);
        response.put("timestamp", System.currentTimeMillis());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/cache/metrics")
    public ResponseEntity<CacheMetricsDTO> getCacheMetrics() {
        CacheMetricsDTO metrics = cacheSillasService.getMetrics();
        return ResponseEntity.ok(metrics);
    }

    @PostMapping("/cache/warmup/{viajeId}")
    public ResponseEntity<MapaSillasDTO> warmupCache(@PathVariable("viajeId") Long viajeId) {
        log.info("[HTTP POST] Solicitud de Warmup de caché para viajeId: {}", viajeId);
        MapaSillasDTO mapa = cacheSillasService.warmupCache(viajeId);
        return ResponseEntity.ok(mapa);
    }
}
