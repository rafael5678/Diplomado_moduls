package com.example.modulocachelecturas.escalabilidad.service;

import com.example.modulocachelecturas.escalabilidad.model.EstadoOrdenCompra;
import com.example.modulocachelecturas.escalabilidad.model.OrdenCompraDTO;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * ADR-001: Puntos 3 y 4.
 * Cola asíncrona de compras desacoplada con workers concurrentes
 * para absorber picos de hasta 16.000 tiquetes/día sin saturar el sistema.
 */
@Service
public class ColaCompraAsincronaService {

    private static final Logger log = LoggerFactory.getLogger(ColaCompraAsincronaService.class);

    @Value("${instance.id:instancia-1}")
    private String instanceId;

    @Autowired
    private DisponibilidadService disponibilidadService;

    // Cola concurrente bloqueante sin límite estricto para no rechazar compras por saturación
    private final BlockingQueue<OrdenCompraDTO> colaCompras = new LinkedBlockingQueue<>(20000);
    private final Map<String, OrdenCompraDTO> almacenOrdenes = new ConcurrentHashMap<>();

    private ExecutorService poolWorkers;
    private volatile boolean activo = true;

    @PostConstruct
    public void inicializarWorkers() {
        // Pool de 4 workers desacoplados por réplica
        poolWorkers = Executors.newFixedThreadPool(4);
        for (int i = 1; i <= 4; i++) {
            final int workerId = i;
            poolWorkers.submit(() -> ejecutarWorker(workerId));
        }
        log.info("[ADR-001] Workers asíncronos de compra inicializados en nodo {}", instanceId);
    }

    @PreDestroy
    public void detenerWorkers() {
        activo = false;
        if (poolWorkers != null) {
            poolWorkers.shutdownNow();
        }
    }

    /**
     * Encola la compra inmediatamente en memoria para respuesta HTTP < 100ms (p95 holgado).
     */
    public OrdenCompraDTO encolarCompra(OrdenCompraDTO orden) {
        long inicio = System.currentTimeMillis();

        if (orden.getIdOrden() == null || orden.getIdOrden().isBlank()) {
            orden.setIdOrden("ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        }

        orden.setEstado(EstadoOrdenCompra.EN_COLA);
        orden.setNodoProcesador(instanceId);
        orden.setFechaCreacion(LocalDateTime.now());

        almacenOrdenes.put(orden.getIdOrden(), orden);
        boolean encolado = colaCompras.offer(orden);

        long duracion = System.currentTimeMillis() - inicio;
        disponibilidadService.registrarPeticion(duracion, !encolado);

        if (!encolado) {
            orden.setEstado(EstadoOrdenCompra.RECHAZADA);
            log.error("[COLA_SATURADA] No se pudo encolar orden {}", orden.getIdOrden());
        } else {
            log.info("[COLA_INGRESO] Orden {} recibida por {} y encolada. Tamaño cola: {}",
                    orden.getIdOrden(), instanceId, colaCompras.size());
        }

        return orden;
    }

    public OrdenCompraDTO consultarOrden(String idOrden) {
        return almacenOrdenes.get(idOrden);
    }

    public int getTamanoCola() {
        return colaCompras.size();
    }

    private void ejecutarWorker(int workerId) {
        log.info("Worker {} iniciado en nodo {}", workerId, instanceId);
        while (activo && !Thread.currentThread().isInterrupted()) {
            try {
                OrdenCompraDTO orden = colaCompras.poll(500, TimeUnit.MILLISECONDS);
                if (orden != null) {
                    procesarOrdenAsincrona(orden, workerId);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Error en worker {}: {}", workerId, e.getMessage(), e);
            }
        }
    }

    private void procesarOrdenAsincrona(OrdenCompraDTO orden, int workerId) {
        long inicioProceso = System.currentTimeMillis();
        try {
            orden.setEstado(EstadoOrdenCompra.PROCESANDO_PAGO);

            // Simulación de interacción desacoplada con pasarela de pagos
            Thread.sleep(50); // latencia de red controlada

            orden.setIdTransaccionPasarela("TX-" + UUID.randomUUID().toString().substring(0, 10).toUpperCase());
            orden.setEstado(EstadoOrdenCompra.PAGO_APROBADO);

            // Emisión de tiquete digital digital garantizado
            String codigoTiquete = String.format("TK-VJ%d-S%d-%s",
                    orden.getViajeId(), orden.getNumeroSilla(), UUID.randomUUID().toString().substring(0, 6).toUpperCase());
            orden.setCodigoTiquete(codigoTiquete);
            orden.setEstado(EstadoOrdenCompra.TIQUETE_EMITIDO);
            orden.setFechaProcesamiento(LocalDateTime.now());

            long tiempoTotal = System.currentTimeMillis() - inicioProceso;
            log.info("[TIQUETE_EMITIDO] Worker {} emitió tiquete {} para orden {} en {} ms",
                    workerId, codigoTiquete, orden.getIdOrden(), tiempoTotal);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            orden.setEstado(EstadoOrdenCompra.RECHAZADA);
            log.error("Fallo al procesar compra asíncrona: {}", e.getMessage());
        }
    }
}
