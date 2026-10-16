package com.example.modulocachelecturas.controller;

import com.example.modulocachelecturas.controller.ViajeDTOs.*;
import com.example.modulocachelecturas.service.CacheSillasService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * CONTROLADOR REST - MÓDULO ADR-002 (Caché Distribuida y Descarga de Lecturas).
 * 
 * Expone la API pública hacia clientes móviles/web y módulos compañeros (Reservas, Pagos).
 */
@RestController                     // Marca esta clase como controlador REST que retorna respuestas en JSON
@RequestMapping("/api/v1")          // Prefijo base de URL para todos los endpoints del módulo
@RequiredArgsConstructor            // Lombok: Inyecta automáticamente dependencias 'final' vía constructor
public class ViajeController {

    private final CacheSillasService cacheSillasService; // Inyección del servicio de negocio de caché

    /**
     * ENDPOINT 1: Consulta del Mapa Interactivo de Sillas (Ruta: GET /api/v1/viajes/{id}/sillas)
     * Responde en milisegundos desde Redis RAM (Hit) o consulta PostgreSQL (Miss).
     */
    @GetMapping("/viajes/{id}/sillas")
    public ResponseEntity<MapaSillasDTO> getMapaSillas(@PathVariable("id") Long id) {
        // 1. Llama al servicio que ejecuta el patrón Cache-Aside con Mutex Lock
        MapaSillasDTO mapa = cacheSillasService.getMapaSillas(id);

        // 2. Agrega cabeceras HTTP para auditoría técnica de rendimiento y caché (ASR 1)
        HttpHeaders headers = new HttpHeaders();
        boolean isHit = mapa.getSource() != null && mapa.getSource().contains("CACHE");
        headers.add("X-Cache", isHit ? "HIT" : "MISS"); // Avisa si el dato vino de RAM o de disco
        headers.add("X-Response-Time-Ms", String.valueOf(mapa.getResponseTimeMs())); // Tiempo de respuesta real

        // 3. Retorna HTTP 200 (OK) con el JSON y las cabeceras
        return ResponseEntity.ok().headers(headers).body(mapa);
    }

    /**
     * ENDPOINT 2: Consulta de Viajes Disponibles (Ruta: GET /api/v1/viajes/disponibles)
     * Permite filtrar por ciudad de origen, destino y fecha de viaje.
     */
    @GetMapping("/viajes/disponibles")
    public ResponseEntity<List<ViajeDisponibleDTO>> getViajesDisponibles(
            @RequestParam(required = false) String origen,   // Parámetro opcional: ej "Bogota"
            @RequestParam(required = false) String destino,  // Parámetro opcional: ej "Medellin"
            @RequestParam(required = false) String fecha) {  // Parámetro opcional: ej "2026-12-01"
        return ResponseEntity.ok(cacheSillasService.getViajesDisponibles(origen, destino, fecha));
    }

    /**
     * ENDPOINT 3: Invalidación de Caché por Evento (Ruta: POST /api/v1/cache/invalidate)
     * Invocado por los módulos de Reservas o Pagos para borrar la memoria al cambiar una silla.
     */
    @PostMapping("/cache/invalidate")
    public ResponseEntity<Map<String, Object>> invalidateCache(@RequestBody InvalidateCacheRequest req) {
        boolean evicted = cacheSillasService.evictMapaSillas(req.getViajeId(), req.getMotivo());
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "viajeId", req.getViajeId(),
                "cacheCleaned", evicted
        ));
    }

    /**
     * ENDPOINT 4: Métricas de Observabilidad y Telemetría (Ruta: GET /api/v1/cache/metrics)
     * Permite verificar en vivo que más del 85% de lecturas se atienden en memoria RAM (ASR 1).
     */
    @GetMapping("/cache/metrics")
    public ResponseEntity<CacheMetricsDTO> getCacheMetrics() {
        return ResponseEntity.ok(cacheSillasService.getMetrics());
    }

    /**
     * ENDPOINT 5: Precalentamiento de Caché (Ruta: POST /api/v1/cache/warmup/{viajeId})
     * Carga el viaje en Redis antes de iniciar la venta para absorber el impacto de usuarios.
     */
    @PostMapping("/cache/warmup/{viajeId}")
    public ResponseEntity<MapaSillasDTO> warmupCache(@PathVariable("viajeId") Long viajeId) {
        return ResponseEntity.ok(cacheSillasService.warmupCache(viajeId));
    }
}
