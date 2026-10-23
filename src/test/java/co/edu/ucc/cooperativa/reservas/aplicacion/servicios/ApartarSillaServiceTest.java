package co.edu.ucc.cooperativa.reservas.aplicacion.servicios;

import co.edu.ucc.cooperativa.reservas.dominio.*;
import co.edu.ucc.cooperativa.reservas.aplicacion.puertos.salida.ReservaRepositoryPort;
import co.edu.ucc.cooperativa.reservas.aplicacion.puertos.salida.SillaCatalogoPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Pruebas Unitarias de Dominio: ApartarSillaService (Cooperativa - ADR-003)")
class ApartarSillaServiceTest {

    @Mock
    private ReservaRepositoryPort reservaRepository;

    @Mock
    private SillaCatalogoPort sillaCatalogo;

    private Clock relojFijo;
    private ApartarSillaService service;

    private final String viajeId = "VJ-COOP-01";
    private final String sillaId = "S-14";
    private final String pasajeroId = "CC-1085123456";

    @BeforeEach
    void setUp() {
        // Reloj inmutable fijado a 2026-10-23 15:00:00 UTC
        relojFijo = Clock.fixed(Instant.parse("2026-10-23T15:00:00Z"), ZoneId.of("UTC"));
        service = new ApartarSillaService(reservaRepository, sillaCatalogo, relojFijo);
    }

    @Test
    @DisplayName("Debe apartar la silla por 10 minutos con estado ACTIVA si no existe reserva previa (ADR-003)")
    void debeApartarSillaExitosamente() {
        LocalDateTime ahora = LocalDateTime.now(relojFijo);

        when(sillaCatalogo.existeEnViaje(viajeId, sillaId)).thenReturn(true);
        when(reservaRepository.existeReservaActiva(viajeId, sillaId, ahora)).thenReturn(false);
        when(reservaRepository.existeReservaConfirmada(viajeId, sillaId)).thenReturn(false);
        when(reservaRepository.guardar(any(Reserva.class))).thenAnswer(inv -> inv.getArgument(0));

        Reserva reserva = service.apartarSilla(viajeId, sillaId, pasajeroId);

        assertNotNull(reserva);
        assertEquals(viajeId, reserva.getViajeId());
        assertEquals(sillaId, reserva.getSillaId());
        assertEquals(pasajeroId, reserva.getPasajeroId());
        assertEquals(EstadoReserva.ACTIVA, reserva.getEstado());
        assertEquals(ahora, reserva.getFechaCreacion());
        assertEquals(ahora.plusMinutes(10), reserva.getFechaExpiracion());
        assertTrue(reserva.estaActiva(ahora));

        verify(reservaRepository).guardar(any(Reserva.class));
    }

    @Test
    @DisplayName("Debe lanzar SillaNoDisponibleException si la silla ya está ACTIVA para evitar doble reserva (ADR-003)")
    void debeEvitarDobleReservaParaMismaSillaYViaje() {
        LocalDateTime ahora = LocalDateTime.now(relojFijo);

        when(sillaCatalogo.existeEnViaje(viajeId, sillaId)).thenReturn(true);
        when(reservaRepository.existeReservaActiva(viajeId, sillaId, ahora)).thenReturn(true);
        when(reservaRepository.existeReservaConfirmada(viajeId, sillaId)).thenReturn(false);

        SillaNoDisponibleException ex = assertThrows(
                SillaNoDisponibleException.class,
                () -> service.apartarSilla(viajeId, sillaId, "OTRO-PASAJERO")
        );

        assertTrue(ex.getMessage().contains("ya se encuentra apartada"));
        verify(reservaRepository, never()).guardar(any());
    }

    @Test
    @DisplayName("Debe permitir reservar la misma silla en viajes diferentes")
    void debePermitirMismaSillaEnViajesDiferentes() {
        LocalDateTime ahora = LocalDateTime.now(relojFijo);
        String otroViaje = "VJ-COOP-02";
        when(sillaCatalogo.existeEnViaje(viajeId, sillaId)).thenReturn(true);
        when(sillaCatalogo.existeEnViaje(otroViaje, sillaId)).thenReturn(true);
        when(reservaRepository.existeReservaActiva(any(), eq(sillaId), eq(ahora))).thenReturn(false);
        when(reservaRepository.existeReservaConfirmada(any(), eq(sillaId))).thenReturn(false);
        when(reservaRepository.guardar(any(Reserva.class))).thenAnswer(inv -> inv.getArgument(0));

        Reserva primera = service.apartarSilla(viajeId, sillaId, pasajeroId);
        Reserva segunda = service.apartarSilla(otroViaje, sillaId, "OTRO-PASAJERO");

        assertEquals(viajeId, primera.getViajeId());
        assertEquals(otroViaje, segunda.getViajeId());
        verify(reservaRepository).guardar(primera);
        verify(reservaRepository).guardar(segunda);
    }

    @Test
    @DisplayName("Debe permitir reservar al llegar exactamente al vencimiento de la reserva anterior")
    void debePermitirNuevaReservaEnElInstanteDeVencimiento() {
        LocalDateTime ahora = LocalDateTime.now(relojFijo);
        when(sillaCatalogo.existeEnViaje(viajeId, sillaId)).thenReturn(true);
        when(reservaRepository.existeReservaActiva(viajeId, sillaId, ahora)).thenReturn(false);
        when(reservaRepository.existeReservaConfirmada(viajeId, sillaId)).thenReturn(false);
        when(reservaRepository.guardar(any(Reserva.class))).thenAnswer(inv -> inv.getArgument(0));

        Reserva nueva = service.apartarSilla(viajeId, sillaId, pasajeroId);

        assertEquals(EstadoReserva.ACTIVA, nueva.getEstado());
        assertEquals(ahora.plusMinutes(10), nueva.getFechaExpiracion());
        verify(reservaRepository).existeReservaActiva(viajeId, sillaId, ahora);
    }

    @Test
    @DisplayName("Debe rechazar una silla que no existe en el viaje y no consultar/guardar reservas")
    void debeRechazarSillaInexistenteEnViaje() {
        when(sillaCatalogo.existeEnViaje(viajeId, sillaId)).thenReturn(false);

        assertThrows(IllegalArgumentException.class,
                () -> service.apartarSilla(viajeId, sillaId, pasajeroId));

        verifyNoInteractions(reservaRepository);
    }

    @Test
    @DisplayName("Una reserva confirmada impide volver a reservar la misma silla en el mismo viaje")
    void debeRechazarSillaConReservaConfirmada() {
        LocalDateTime ahora = LocalDateTime.now(relojFijo);
        when(sillaCatalogo.existeEnViaje(viajeId, sillaId)).thenReturn(true);
        when(reservaRepository.existeReservaActiva(viajeId, sillaId, ahora)).thenReturn(false);
        when(reservaRepository.existeReservaConfirmada(viajeId, sillaId)).thenReturn(true);

        assertThrows(SillaNoDisponibleException.class,
                () -> service.apartarSilla(viajeId, sillaId, "OTRO-PASAJERO"));
        verify(reservaRepository, never()).guardar(any());
    }

    @Test
    @DisplayName("Debe confirmar una reserva activa dentro de la ventana de 10 minutos")
    void debeConfirmarReservaVigente() {
        LocalDateTime ahora = LocalDateTime.now(relojFijo);
        Reserva reservaActiva = Reserva.crear(viajeId, sillaId, pasajeroId, ahora.minusMinutes(4));

        when(reservaRepository.buscarPorId(reservaActiva.getId())).thenReturn(Optional.of(reservaActiva));
        when(reservaRepository.guardar(any(Reserva.class))).thenAnswer(inv -> inv.getArgument(0));

        Reserva confirmada = service.confirmarReserva(reservaActiva.getId());

        assertEquals(EstadoReserva.CONFIRMADA, confirmada.getEstado());
        assertEquals(ahora, confirmada.getFechaConfirmacion());
        verify(reservaRepository).guardar(reservaActiva);
    }

    @Test
    @DisplayName("Debe lanzar ReservaExpiradaException al intentar confirmar una reserva con más de 10 minutos")
    void debeFallarConfirmacionAlExpirarTiempo() {
        LocalDateTime ahora = LocalDateTime.now(relojFijo);
        // Creada hace 11 minutos -> ya venció la ventana de 10 minutos
        Reserva reservaVencida = Reserva.crear(viajeId, sillaId, pasajeroId, ahora.minusMinutes(11));

        when(reservaRepository.buscarPorId(reservaVencida.getId())).thenReturn(Optional.of(reservaVencida));

        assertThrows(
                ReservaExpiradaException.class,
                () -> service.confirmarReserva(reservaVencida.getId())
        );

        assertEquals(EstadoReserva.EXPIRADA, reservaVencida.getEstado());
        verify(reservaRepository, never()).guardar(any());
    }

    @Test
    @DisplayName("No debe confirmar una reserva exactamente al cumplir 10 minutos")
    void debeFallarConfirmacionEnInstanteExactoDeExpiracion() {
        LocalDateTime ahora = LocalDateTime.now(relojFijo);
        Reserva reservaVencida = Reserva.crear(viajeId, sillaId, pasajeroId, ahora.minusMinutes(10));
        when(reservaRepository.buscarPorId(reservaVencida.getId())).thenReturn(Optional.of(reservaVencida));

        assertThrows(ReservaExpiradaException.class,
                () -> service.confirmarReserva(reservaVencida.getId()));
        assertEquals(EstadoReserva.EXPIRADA, reservaVencida.getEstado());
        verify(reservaRepository, never()).guardar(any());
    }

    @Test
    @DisplayName("Debe consultar una reserva existente por ID")
    void debeConsultarReservaPorId() {
        Reserva mockReserva = Reserva.crear(viajeId, sillaId, pasajeroId, LocalDateTime.now(relojFijo));
        when(reservaRepository.buscarPorId("R-123")).thenReturn(Optional.of(mockReserva));

        Optional<Reserva> encontrada = service.consultarReserva("R-123");

        assertTrue(encontrada.isPresent());
        assertEquals(viajeId, encontrada.get().getViajeId());
        assertEquals(sillaId, encontrada.get().getSillaId());
    }

    @Test
    @DisplayName("Debe validar y rechazar argumentos nulos o en blanco")
    void debeRechazarDatosInvalidos() {
        assertThrows(IllegalArgumentException.class, () -> service.apartarSilla(null, sillaId, pasajeroId));
        assertThrows(IllegalArgumentException.class, () -> service.apartarSilla("   ", sillaId, pasajeroId));
        assertThrows(IllegalArgumentException.class, () -> service.apartarSilla(viajeId, null, pasajeroId));
        assertThrows(IllegalArgumentException.class, () -> service.apartarSilla(viajeId, " ", pasajeroId));
        assertThrows(IllegalArgumentException.class, () -> service.apartarSilla(viajeId, sillaId, null));
        assertThrows(IllegalArgumentException.class, () -> service.apartarSilla(viajeId, sillaId, ""));
    }
}
