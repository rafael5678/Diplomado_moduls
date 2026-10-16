package com.example.modulocachelecturas;

import com.example.modulocachelecturas.model.EstadoSilla;
import com.example.modulocachelecturas.model.Silla;
import com.example.modulocachelecturas.reservas.model.EstadoReserva;
import com.example.modulocachelecturas.reservas.model.ReservaSilla;
import com.example.modulocachelecturas.reservas.repository.ReservaSillaRepository;
import com.example.modulocachelecturas.reservas.repository.SillaReservaRepository;
import com.example.modulocachelecturas.reservas.service.ReservaExceptions.RecursoNoEncontradoException;
import com.example.modulocachelecturas.reservas.service.ReservaExceptions.ReservaNoModificableException;
import com.example.modulocachelecturas.reservas.service.ReservaExceptions.SillaNoDisponibleException;
import com.example.modulocachelecturas.reservas.service.ReservaSillaService;
import com.example.modulocachelecturas.reservas.service.SillaCambioEstadoEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * PRUEBAS DEL MÓDULO DE RESERVAS (ADR-004: cada silla se aparta y se vende una sola vez).
 *
 * Usan un reloj fijo (20 de diciembre, 8:00 a.m.) para probar el vencimiento de 10 minutos sin esperar.
 */
@ExtendWith(MockitoExtension.class)
class ReservaSillaServiceTest {

    @Mock
    private ReservaSillaRepository reservaRepository;

    @Mock
    private SillaReservaRepository sillaRepository;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private ApplicationEventPublisher eventos;

    private ReservaSillaService service;
    private LocalDateTime ahora;

    private static final Long VIAJE_ID = 1L;
    private static final Integer SILLA = 12;

    @BeforeEach
    void setUp() {
        ZoneId zona = ZoneId.of("America/Bogota");
        Clock reloj = Clock.fixed(Instant.parse("2026-12-20T13:00:00Z"), zona); // 8:00 a.m. en Colombia
        ahora = LocalDateTime.now(reloj);
        service = new ReservaSillaService(reservaRepository, sillaRepository, transactionManager, eventos, reloj, 10);
    }

    // ------------------------------------------------------------------------
    // Apartar
    // ------------------------------------------------------------------------

    @Test
    @DisplayName("ASR 2 - Apartar: una silla disponible queda APARTADA 10 minutos y se avisa a la caché")
    void apartar_sillaDisponible_quedaApartadaDiezMinutos() {
        when(sillaRepository.findByViajeIdAndNumeroSilla(VIAJE_ID, SILLA))
                .thenReturn(Optional.of(silla(EstadoSilla.DISPONIBLE)));
        when(reservaRepository.saveAndFlush(any(ReservaSilla.class))).thenAnswer(inv -> {
            ReservaSilla r = inv.getArgument(0);
            r.setId(100L);
            return r;
        });

        ReservaSilla reserva = service.apartar(VIAJE_ID, SILLA, "1085123456");

        assertEquals(100L, reserva.getId());
        assertEquals(EstadoReserva.APARTADA, reserva.getEstado());
        assertEquals("1:12", reserva.getLlaveActiva());
        assertEquals(ahora.plusMinutes(10), reserva.getVenceEn());

        verify(sillaRepository).actualizarEstado(VIAJE_ID, SILLA, EstadoSilla.RESERVADA);
        verify(eventos).publishEvent(new SillaCambioEstadoEvent(VIAJE_ID, SILLA, "SILLA_APARTADA"));
    }

    @Test
    @DisplayName("ASR 2 - Dos pasajeros, misma silla: la base de datos rechaza al segundo y se le avisa")
    void apartar_dosPasajerosMismaSilla_elSegundoRecibeSillaNoDisponible() {
        // Los dos ven la silla DISPONIBLE (el mapa puede estar atrasado),
        // pero la restricción UNIQUE de 'llave_activa' solo deja pasar la primera inserción.
        when(sillaRepository.findByViajeIdAndNumeroSilla(VIAJE_ID, SILLA))
                .thenReturn(Optional.of(silla(EstadoSilla.DISPONIBLE)));
        when(reservaRepository.saveAndFlush(any(ReservaSilla.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint \"uq_reserva_silla_activa\""));

        SillaNoDisponibleException error = assertThrows(SillaNoDisponibleException.class,
                () -> service.apartar(VIAJE_ID, SILLA, "1085999999"));

        assertTrue(error.getMessage().contains("ya está ocupada"));
        verify(sillaRepository, never()).actualizarEstado(anyLong(), anyInt(), any());
        verify(eventos, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("Apartar: una silla OCUPADA o RESERVADA no se puede apartar")
    void apartar_sillaOcupada_lanzaSillaNoDisponible() {
        when(sillaRepository.findByViajeIdAndNumeroSilla(VIAJE_ID, SILLA))
                .thenReturn(Optional.of(silla(EstadoSilla.OCUPADA)));

        assertThrows(SillaNoDisponibleException.class, () -> service.apartar(VIAJE_ID, SILLA, "1085123456"));

        verify(reservaRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Apartar: una silla que no existe en el viaje responde 'no encontrado'")
    void apartar_sillaInexistente_lanzaNoEncontrado() {
        when(sillaRepository.findByViajeIdAndNumeroSilla(VIAJE_ID, 99)).thenReturn(Optional.empty());

        assertThrows(RecursoNoEncontradoException.class, () -> service.apartar(VIAJE_ID, 99, "1085123456"));
    }

    @Test
    @DisplayName("Apartar: sin pasajero no se aparta nada")
    void apartar_sinPasajero_lanzaDatosInvalidos() {
        assertThrows(IllegalArgumentException.class, () -> service.apartar(VIAJE_ID, SILLA, " "));
        verifyNoInteractions(sillaRepository, reservaRepository);
    }

    // ------------------------------------------------------------------------
    // Confirmar (lo llama Pagos)
    // ------------------------------------------------------------------------

    @Test
    @DisplayName("Confirmar: una reserva vigente pasa a VENDIDA y la silla a OCUPADA")
    void confirmar_reservaVigente_quedaVendida() {
        when(reservaRepository.findById(100L))
                .thenReturn(Optional.of(reserva(EstadoReserva.APARTADA, ahora.plusMinutes(5))))
                .thenReturn(Optional.of(reserva(EstadoReserva.VENDIDA, ahora.plusMinutes(5))));
        when(reservaRepository.marcarVendidaSiVigente(100L, ahora, EstadoReserva.APARTADA, EstadoReserva.VENDIDA))
                .thenReturn(1);

        ReservaSilla resultado = service.confirmar(100L);

        assertEquals(EstadoReserva.VENDIDA, resultado.getEstado());
        verify(sillaRepository).actualizarEstado(VIAJE_ID, SILLA, EstadoSilla.OCUPADA);
        verify(eventos).publishEvent(new SillaCambioEstadoEvent(VIAJE_ID, SILLA, "SILLA_VENDIDA"));
    }

    @Test
    @DisplayName("Confirmar dos veces: la segunda no cambia nada (la pasarela puede avisar dos veces)")
    void confirmar_repetido_esIdempotente() {
        when(reservaRepository.findById(100L))
                .thenReturn(Optional.of(reserva(EstadoReserva.VENDIDA, ahora.plusMinutes(5))));
        when(reservaRepository.marcarVendidaSiVigente(100L, ahora, EstadoReserva.APARTADA, EstadoReserva.VENDIDA))
                .thenReturn(0);

        ReservaSilla resultado = service.confirmar(100L);

        assertEquals(EstadoReserva.VENDIDA, resultado.getEstado());
        verify(sillaRepository, never()).actualizarEstado(anyLong(), anyInt(), any());
        verify(eventos, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("Confirmar después de los 10 minutos: se rechaza para que Pagos devuelva el dinero")
    void confirmar_reservaVencida_seRechaza() {
        when(reservaRepository.findById(100L))
                .thenReturn(Optional.of(reserva(EstadoReserva.APARTADA, ahora.minusMinutes(1))));
        when(reservaRepository.marcarVendidaSiVigente(100L, ahora, EstadoReserva.APARTADA, EstadoReserva.VENDIDA))
                .thenReturn(0);

        assertThrows(ReservaNoModificableException.class, () -> service.confirmar(100L));
        verify(sillaRepository, never()).actualizarEstado(anyLong(), anyInt(), any());
    }

    // ------------------------------------------------------------------------
    // Cancelar y liberar vencidas
    // ------------------------------------------------------------------------

    @Test
    @DisplayName("Cancelar: una reserva APARTADA se libera y la silla vuelve a DISPONIBLE")
    void cancelar_reservaApartada_liberaLaSilla() {
        when(reservaRepository.findById(100L))
                .thenReturn(Optional.of(reserva(EstadoReserva.APARTADA, ahora.plusMinutes(5))))
                .thenReturn(Optional.of(reserva(EstadoReserva.LIBERADA, ahora.plusMinutes(5))));
        when(reservaRepository.marcarLiberadaSiApartada(100L, ahora, EstadoReserva.APARTADA, EstadoReserva.LIBERADA))
                .thenReturn(1);

        ReservaSilla resultado = service.cancelar(100L);

        assertEquals(EstadoReserva.LIBERADA, resultado.getEstado());
        verify(sillaRepository).actualizarEstado(VIAJE_ID, SILLA, EstadoSilla.DISPONIBLE);
        verify(eventos).publishEvent(new SillaCambioEstadoEvent(VIAJE_ID, SILLA, "RESERVA_CANCELADA"));
    }

    @Test
    @DisplayName("Cancelar: una reserva ya pagada no se puede cancelar por aquí")
    void cancelar_reservaVendida_seRechaza() {
        when(reservaRepository.findById(100L))
                .thenReturn(Optional.of(reserva(EstadoReserva.VENDIDA, ahora.plusMinutes(5))));
        when(reservaRepository.marcarLiberadaSiApartada(100L, ahora, EstadoReserva.APARTADA, EstadoReserva.LIBERADA))
                .thenReturn(0);

        assertThrows(ReservaNoModificableException.class, () -> service.cancelar(100L));
        verify(sillaRepository, never()).actualizarEstado(anyLong(), anyInt(), any());
    }

    @Test
    @DisplayName("Liberador: libera las vencidas; si una se vendió en el último segundo, no la toca")
    void liberarVencidas_liberaSoloLasQueSiguenApartadas() {
        ReservaSilla vencida = reserva(100L, 12, EstadoReserva.APARTADA, ahora.minusMinutes(2));
        ReservaSilla vendidaAlUltimoSegundo = reserva(101L, 13, EstadoReserva.APARTADA, ahora.minusMinutes(1));

        when(reservaRepository.buscarVencidas(eq(EstadoReserva.APARTADA), eq(ahora), any()))
                .thenReturn(List.of(vencida, vendidaAlUltimoSegundo));
        when(reservaRepository.marcarLiberadaSiApartada(100L, ahora, EstadoReserva.APARTADA, EstadoReserva.LIBERADA))
                .thenReturn(1);
        when(reservaRepository.marcarLiberadaSiApartada(101L, ahora, EstadoReserva.APARTADA, EstadoReserva.LIBERADA))
                .thenReturn(0);

        int liberadas = service.liberarVencidas();

        assertEquals(1, liberadas);
        verify(sillaRepository).actualizarEstado(VIAJE_ID, 12, EstadoSilla.DISPONIBLE);
        verify(sillaRepository, never()).actualizarEstado(VIAJE_ID, 13, EstadoSilla.DISPONIBLE);
        verify(eventos).publishEvent(new SillaCambioEstadoEvent(VIAJE_ID, 12, "RESERVA_VENCIDA"));
    }

    // ------------------------------------------------------------------------
    // Datos de prueba
    // ------------------------------------------------------------------------

    private Silla silla(EstadoSilla estado) {
        return Silla.builder()
                .id(500L)
                .numeroSilla(SILLA)
                .piso(1)
                .ubicacion("VENTANA")
                .estado(estado)
                .precio(new BigDecimal("95000.00"))
                .build();
    }

    private ReservaSilla reserva(EstadoReserva estado, LocalDateTime venceEn) {
        return reserva(100L, SILLA, estado, venceEn);
    }

    private ReservaSilla reserva(Long id, Integer numeroSilla, EstadoReserva estado, LocalDateTime venceEn) {
        return ReservaSilla.builder()
                .id(id)
                .viajeId(VIAJE_ID)
                .numeroSilla(numeroSilla)
                .pasajeroId("1085123456")
                .estado(estado)
                .llaveActiva(estado == EstadoReserva.LIBERADA ? null : ReservaSilla.llaveDe(VIAJE_ID, numeroSilla))
                .creadaEn(venceEn.minusMinutes(10))
                .venceEn(venceEn)
                .build();
    }
}
