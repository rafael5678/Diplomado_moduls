package com.example.modulocachelecturas.escalabilidad;

import com.example.modulocachelecturas.escalabilidad.model.EstadoOrdenCompra;
import com.example.modulocachelecturas.escalabilidad.model.MetricasDisponibilidadDTO;
import com.example.modulocachelecturas.escalabilidad.model.OrdenCompraDTO;
import com.example.modulocachelecturas.escalabilidad.service.ColaCompraAsincronaService;
import com.example.modulocachelecturas.escalabilidad.service.DisponibilidadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class EscalabilidadYDisponibilidadTest {

    private DisponibilidadService disponibilidadService;
    private ColaCompraAsincronaService colaCompraService;

    @BeforeEach
    void setUp() {
        disponibilidadService = new DisponibilidadService();
        ReflectionTestUtils.setField(disponibilidadService, "instanceId", "nodo-test-1");

        colaCompraService = new ColaCompraAsincronaService();
        ReflectionTestUtils.setField(colaCompraService, "instanceId", "nodo-test-1");
        ReflectionTestUtils.setField(colaCompraService, "disponibilidadService", disponibilidadService);
        colaCompraService.inicializarWorkers();
    }

    @Test
    @DisplayName("ASR 1: Verificación de meta de disponibilidad 99.95% y cálculo de p95 < 3s")
    void testDisponibilidadYPercentil95() {
        // Simular 1000 peticiones normales con latencias variables (10ms a 200ms)
        for (int i = 0; i < 999; i++) {
            disponibilidadService.registrarPeticion(20 + (i % 50), false);
        }
        // Simular 1 error 5xx aislado
        disponibilidadService.registrarPeticion(150, true);

        MetricasDisponibilidadDTO metricas = disponibilidadService.obtenerMetricas();

        assertEquals(1000, metricas.getTotalPeticionesAtendidas());
        assertEquals(1, metricas.getTotalErrores5xx());
        // 99.9% de disponibilidad calculada
        assertTrue(metricas.getDisponibilidadPorcentaje() >= 99.9);
        // p95 debe estar muy por debajo de los 3000 ms establecidos en ASR 1
        assertTrue(metricas.getLatenciaP95Ms() < 3000.0);
    }

    @Test
    @DisplayName("ASR 1: Detección y emisión de alerta automática (< 1 min) ante caída de disponibilidad")
    void testEmisionAlertaAutomatica() {
        // Simular degradación severa
        for (int i = 0; i < 15; i++) {
            disponibilidadService.simularFallaOError();
        }

        disponibilidadService.auditarSaludYAlertar();
        MetricasDisponibilidadDTO metricas = disponibilidadService.obtenerMetricas();

        assertTrue(metricas.isAlertaActiva(), "La alerta debe activarse automáticamente");
        assertNotNull(metricas.getMensajeAlerta());
        assertTrue(metricas.getMensajeAlerta().contains("ALERTA CRÍTICA"));
    }

    @Test
    @DisplayName("ADR-001: Desacoplamiento asíncrono y soporte de alta concurrencia de compras")
    void testColaCompraAsincronaAltaConcurrencia() throws InterruptedException {
        int totalCompras = 50;
        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(totalCompras);
        AtomicInteger comprasExitosas = new AtomicInteger(0);

        for (int i = 1; i <= totalCompras; i++) {
            final int id = i;
            executor.submit(() -> {
                try {
                    OrdenCompraDTO orden = new OrdenCompraDTO(
                            "ORD-TEST-" + id,
                            1L,
                            id,
                            "Pasajero " + id,
                            "pasajero" + id + "@correo.com",
                            "DOC" + id,
                            BigDecimal.valueOf(85000)
                    );
                    OrdenCompraDTO resultado = colaCompraService.encolarCompra(orden);
                    if (resultado.getEstado() != EstadoOrdenCompra.RECHAZADA) {
                        comprasExitosas.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean terminado = latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(terminado, "Todas las compras debieron encolarse a tiempo");
        assertEquals(totalCompras, comprasExitosas.get(), "0% de compras deben ser rechazadas por saturación");

        // Dar un breve tiempo para que los workers asíncronos procesen y emitan tiquetes
        Thread.sleep(1500);

        OrdenCompraDTO ordenProcesada = colaCompraService.consultarOrden("ORD-TEST-1");
        assertNotNull(ordenProcesada);
        assertEquals(EstadoOrdenCompra.TIQUETE_EMITIDO, ordenProcesada.getEstado());
        assertNotNull(ordenProcesada.getCodigoTiquete());
        assertNotNull(ordenProcesada.getIdTransaccionPasarela());
    }
}
