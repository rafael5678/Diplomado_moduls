package com.example.modulocachelecturas.escalabilidad.controller;

import com.example.modulocachelecturas.escalabilidad.model.MetricasDisponibilidadDTO;
import com.example.modulocachelecturas.escalabilidad.model.OrdenCompraDTO;
import com.example.modulocachelecturas.escalabilidad.service.ColaCompraAsincronaService;
import com.example.modulocachelecturas.escalabilidad.service.DisponibilidadService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/escalabilidad")
public class EscalabilidadController {

    @Autowired
    private DisponibilidadService disponibilidadService;

    @Autowired
    private ColaCompraAsincronaService colaCompraService;

    /**
     * Endpoint para consultar métricas de disponibilidad y latencia P95 (ASR-001).
     */
    @GetMapping("/metricas")
    public ResponseEntity<MetricasDisponibilidadDTO> obtenerMetricas() {
        return ResponseEntity.ok(disponibilidadService.obtenerMetricas());
    }

    /**
     * Endpoint de compra asíncrona desacoplada (ADR-001).
     * Encola la petición de compra y retorna inmediatamente respuesta 202 ACCEPTED.
     */
    @PostMapping("/compras")
    public ResponseEntity<OrdenCompraDTO> encolarCompra(@RequestBody OrdenCompraDTO compra) {
        OrdenCompraDTO resultado = colaCompraService.encolarCompra(compra);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(resultado);
    }

    /**
     * Consulta el estado de una orden de compra encolada/procesada.
     */
    @GetMapping("/compras/{idOrden}")
    public ResponseEntity<?> consultarCompra(@PathVariable String idOrden) {
        OrdenCompraDTO orden = colaCompraService.consultarOrden(idOrden);
        if (orden == null) {
            Map<String, Object> error = new HashMap<>();
            error.put("status", 404);
            error.put("mensaje", "Orden no encontrada: " + idOrden);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        }
        return ResponseEntity.ok(orden);
    }

    /**
     * Endpoint para simular falla o alta latencia y verificar emisión de alertas automáticas (< 1 min).
     */
    @PostMapping("/simular-falla")
    public ResponseEntity<Map<String, String>> simularFalla() {
        disponibilidadService.simularFallaOError();
        Map<String, String> resp = new HashMap<>();
        resp.put("nodo", disponibilidadService.getInstanceId());
        resp.put("mensaje", "Falla simulada exitosamente en este nodo. Verifique la emisión de alertas y el failover en el balanceador.");
        return ResponseEntity.ok(resp);
    }

    /**
     * Reiniciar métricas acumuladas del nodo.
     */
    @PostMapping("/reiniciar-metricas")
    public ResponseEntity<Map<String, String>> reiniciarMetricas() {
        disponibilidadService.reiniciarMetricas();
        Map<String, String> resp = new HashMap<>();
        resp.put("nodo", disponibilidadService.getInstanceId());
        resp.put("mensaje", "Métricas reiniciadas a 100% de disponibilidad.");
        return ResponseEntity.ok(resp);
    }
}
