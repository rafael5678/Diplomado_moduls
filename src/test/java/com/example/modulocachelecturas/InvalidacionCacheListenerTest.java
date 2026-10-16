package com.example.modulocachelecturas;

import com.example.modulocachelecturas.reservas.service.InvalidacionCacheListener;
import com.example.modulocachelecturas.reservas.service.SillaCambioEstadoEvent;
import com.example.modulocachelecturas.service.CacheSillasService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

/**
 * PRUEBAS DE LA UNIÓN ENTRE EL MÓDULO DE RESERVAS Y EL MÓDULO DE CACHÉ.
 */
@ExtendWith(MockitoExtension.class)
class InvalidacionCacheListenerTest {

    @Mock
    private CacheSillasService cacheSillasService;

    @InjectMocks
    private InvalidacionCacheListener listener;

    @Test
    @DisplayName("Al apartar una silla se borra el mapa de ese viaje en Redis")
    void alCambiarSilla_borraElMapaDelViaje() {
        listener.alCambiarEstadoSilla(new SillaCambioEstadoEvent(1L, 12, "SILLA_APARTADA"));

        verify(cacheSillasService).evictMapaSillas(1L, "SILLA_APARTADA");
    }

    @Test
    @DisplayName("Si Redis falla, la reserva no se cae: solo se registra el aviso")
    void siLaCacheFalla_noPropagaElError() {
        when(cacheSillasService.evictMapaSillas(1L, "SILLA_VENDIDA"))
                .thenThrow(new RuntimeException("Redis no responde"));

        assertDoesNotThrow(() ->
                listener.alCambiarEstadoSilla(new SillaCambioEstadoEvent(1L, 12, "SILLA_VENDIDA")));
    }
}
