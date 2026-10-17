package com.example.modulocachelecturas.escalabilidad;

import com.example.modulocachelecturas.escalabilidad.controller.EscalabilidadController;
import com.example.modulocachelecturas.escalabilidad.model.EstadoOrdenCompra;
import com.example.modulocachelecturas.escalabilidad.model.MetricasDisponibilidadDTO;
import com.example.modulocachelecturas.escalabilidad.model.OrdenCompraDTO;
import com.example.modulocachelecturas.escalabilidad.service.ColaCompraAsincronaService;
import com.example.modulocachelecturas.escalabilidad.service.DisponibilidadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class EscalabilidadControllerTest {

    @Mock
    private DisponibilidadService disponibilidadService;

    @Mock
    private ColaCompraAsincronaService colaCompraService;

    @InjectMocks
    private EscalabilidadController controller;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    @DisplayName("GET /api/v1/escalabilidad/metricas: debe retornar 200 OK con métricas de disponibilidad y percentil 95")
    void testObtenerMetricas() {
        MetricasDisponibilidadDTO mockMetricas = new MetricasDisponibilidadDTO(
                "nodo-1", 99.98, 1200L, 0L, 45.2, 120.0, false, "Nominal", LocalDateTime.now()
        );
        when(disponibilidadService.obtenerMetricas()).thenReturn(mockMetricas);

        ResponseEntity<MetricasDisponibilidadDTO> respuesta = controller.obtenerMetricas();

        assertEquals(HttpStatus.OK, respuesta.getStatusCode());
        assertNotNull(respuesta.getBody());
        assertEquals("nodo-1", respuesta.getBody().getInstanceId());
        assertEquals(99.98, respuesta.getBody().getDisponibilidadPorcentaje());
        assertEquals(120.0, respuesta.getBody().getLatenciaP95Ms());
        assertFalse(respuesta.getBody().isAlertaActiva());

        verify(disponibilidadService, times(1)).obtenerMetricas();
    }

    @Test
    @DisplayName("POST /api/v1/escalabilidad/compras: debe aceptar compra asíncrona retornando 202 ACCEPTED")
    void testEncolarCompra() {
        OrdenCompraDTO ordenEntrante = new OrdenCompraDTO("ORD-100", 1L, 5, "Carlos Gomez", "carlos@correo.com", "CC123", BigDecimal.valueOf(85000));
        OrdenCompraDTO ordenRespuesta = new OrdenCompraDTO("ORD-100", 1L, 5, "Carlos Gomez", "carlos@correo.com", "CC123", BigDecimal.valueOf(85000));
        ordenRespuesta.setEstado(EstadoOrdenCompra.EN_COLA);
        ordenRespuesta.setNodoProcesador("nodo-1");

        when(colaCompraService.encolarCompra(any(OrdenCompraDTO.class))).thenReturn(ordenRespuesta);

        ResponseEntity<OrdenCompraDTO> respuesta = controller.encolarCompra(ordenEntrante);

        assertEquals(HttpStatus.ACCEPTED, respuesta.getStatusCode());
        assertNotNull(respuesta.getBody());
        assertEquals("ORD-100", respuesta.getBody().getIdOrden());
        assertEquals(EstadoOrdenCompra.EN_COLA, respuesta.getBody().getEstado());

        verify(colaCompraService, times(1)).encolarCompra(any(OrdenCompraDTO.class));
    }

    @Test
    @DisplayName("GET /api/v1/escalabilidad/compras/{id}: debe retornar 404 NOT_FOUND si la orden no existe")
    void testConsultarCompraInexistente() {
        when(colaCompraService.consultarOrden("ORD-INEXISTENTE")).thenReturn(null);

        ResponseEntity<?> respuesta = controller.consultarCompra("ORD-INEXISTENTE");

        assertEquals(HttpStatus.NOT_FOUND, respuesta.getStatusCode());
        assertNotNull(respuesta.getBody());
        assertTrue(respuesta.getBody() instanceof Map);
        Map<?, ?> errorMap = (Map<?, ?>) respuesta.getBody();
        assertEquals(404, errorMap.get("status"));
    }

    @Test
    @DisplayName("GET /api/v1/escalabilidad/compras/{id}: debe retornar 200 OK con la orden encontrada")
    void testConsultarCompraExistente() {
        OrdenCompraDTO orden = new OrdenCompraDTO("ORD-200", 1L, 10, "Ana Lopez", "ana@correo.com", "CC456", BigDecimal.valueOf(95000));
        orden.setEstado(EstadoOrdenCompra.TIQUETE_EMITIDO);
        orden.setCodigoTiquete("TK-VJ1-S10-1234");

        when(colaCompraService.consultarOrden("ORD-200")).thenReturn(orden);

        ResponseEntity<?> respuesta = controller.consultarCompra("ORD-200");

        assertEquals(HttpStatus.OK, respuesta.getStatusCode());
        assertNotNull(respuesta.getBody());
        assertTrue(respuesta.getBody() instanceof OrdenCompraDTO);
        OrdenCompraDTO body = (OrdenCompraDTO) respuesta.getBody();
        assertEquals("ORD-200", body.getIdOrden());
        assertEquals(EstadoOrdenCompra.TIQUETE_EMITIDO, body.getEstado());
    }

    @Test
    @DisplayName("POST /api/v1/escalabilidad/simular-falla: debe activar alerta y retornar confirmación")
    void testSimularFalla() {
        when(disponibilidadService.getInstanceId()).thenReturn("nodo-1");

        ResponseEntity<Map<String, String>> respuesta = controller.simularFalla();

        assertEquals(HttpStatus.OK, respuesta.getStatusCode());
        assertNotNull(respuesta.getBody());
        assertEquals("nodo-1", respuesta.getBody().get("nodo"));
        assertTrue(respuesta.getBody().get("mensaje").contains("Falla simulada"));

        verify(disponibilidadService, times(1)).simularFallaOError();
    }
}
