package com.example.modulocachelecturas.escalabilidad.service;

import com.example.modulocachelecturas.escalabilidad.model.MetricasDisponibilidadDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class DisponibilidadService {

    private static final Logger log = LoggerFactory.getLogger(DisponibilidadService.class);

    @Value("${instance.id:instancia-1}")
    private String instanceId;

    private final AtomicLong totalPeticiones = new AtomicLong(0);
    private final AtomicLong totalErrores5xx = new AtomicLong(0);
    private final List<Long> latenciasRecientes = Collections.synchronizedList(new ArrayList<>());

    private volatile boolean alertaActiva = false;
    private volatile String mensajeAlerta = "Sistema operando nominalmente.";
    private volatile LocalDateTime ultimaAlerta = null;

    /**
     * Registra cada petición atendida por este nodo y su tiempo de respuesta en ms.
     */
    public void registrarPeticion(long duracionMs, boolean esError5xx) {
        totalPeticiones.incrementAndGet();
        if (esError5xx) {
            totalErrores5xx.incrementAndGet();
        }

        // Mantener un historial circular para calcular percentiles recientes
        synchronized (latenciasRecientes) {
            if (latenciasRecientes.size() >= 5000) {
                latenciasRecientes.remove(0);
            }
            latenciasRecientes.add(duracionMs);
        }
    }

    /**
     * Calcula las métricas de disponibilidad y el percentil 95 (ASR 1).
     */
    public MetricasDisponibilidadDTO obtenerMetricas() {
        long peticiones = totalPeticiones.get();
        long errores = totalErrores5xx.get();

        double disponibilidad = 100.0;
        if (peticiones > 0) {
            disponibilidad = ((double) (peticiones - errores) / peticiones) * 100.0;
        }

        double latenciaPromedio = 0;
        double p95 = 0;

        List<Long> copia;
        synchronized (latenciasRecientes) {
            copia = new ArrayList<>(latenciasRecientes);
        }

        if (!copia.isEmpty()) {
            latenciaPromedio = copia.stream().mapToLong(Long::longValue).average().orElse(0.0);
            Collections.sort(copia);
            int idxP95 = (int) Math.ceil(copia.size() * 0.95) - 1;
            if (idxP95 < 0) idxP95 = 0;
            p95 = copia.get(idxP95);
        }

        return new MetricasDisponibilidadDTO(
                instanceId,
                Math.round(disponibilidad * 100.0) / 100.0,
                peticiones,
                errores,
                Math.round(latenciaPromedio * 10.0) / 10.0,
                p95,
                alertaActiva,
                mensajeAlerta,
                LocalDateTime.now()
        );
    }

    /**
     * Tarea periódica de supervisión en tiempo real (< 1 minuto según ASR 1).
     * Si la disponibilidad cae por debajo del 99.95% o el p95 supera los 3000 ms,
     * dispara una alarma inmediata para el equipo de 4 desarrolladores.
     */
    @Scheduled(fixedRate = 30000) // Cada 30 segundos
    public void auditarSaludYAlertar() {
        MetricasDisponibilidadDTO m = obtenerMetricas();

        if (m.getTotalPeticionesAtendidas() >= 10) {
            if (m.getDisponibilidadPorcentaje() < 99.95 || m.getLatenciaP95Ms() > 3000) {
                alertaActiva = true;
                mensajeAlerta = String.format("ALERTA CRÍTICA [ASR-001] en %s: Disponibilidad actual: %.2f%%, Latencia p95: %.1f ms. Requiere atención inmediata.",
                        instanceId, m.getDisponibilidadPorcentaje(), m.getLatenciaP95Ms());
                ultimaAlerta = LocalDateTime.now();
                log.error("[ALERTA_DISPONIBILIDAD_AUTOMATICA] {}", mensajeAlerta);
            } else {
                if (alertaActiva) {
                    log.info("[ALERTA_RECUPERADA] El nodo {} se ha estabilizado. Disponibilidad: %.2f%%", instanceId, m.getDisponibilidadPorcentaje());
                }
                alertaActiva = false;
                mensajeAlerta = "Disponibilidad óptima dentro de los umbrales de ASR-001 (>= 99.95%).";
            }
        }
    }

    public void simularFallaOError() {
        totalPeticiones.incrementAndGet();
        totalErrores5xx.incrementAndGet();
        alertaActiva = true;
        mensajeAlerta = "Falla inducida manualmente para validación de failover y alerta.";
        ultimaAlerta = LocalDateTime.now();
    }

    public void reiniciarMetricas() {
        totalPeticiones.set(0);
        totalErrores5xx.set(0);
        latenciasRecientes.clear();
        alertaActiva = false;
        mensajeAlerta = "Sistema operando nominalmente.";
    }

    public String getInstanceId() {
        return instanceId;
    }
}
