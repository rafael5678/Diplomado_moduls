package co.edu.ucc.cooperativa.reservas.dominio;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Pruebas Unitarias de Dominio: Entidad Reserva (ADR-003: 10 minutos y estado ACTIVA)")
class ReservaTest {

    private LocalDateTime ahora;
    private Reserva reserva;

    @BeforeEach
    void setUp() {
        ahora = LocalDateTime.of(2026, 10, 23, 14, 0, 0);
        reserva = Reserva.crear("VJ-100", "S-05", "PASAJERO-999", ahora);
    }

    @Test
    @DisplayName("Debe crear una reserva en estado ACTIVA con exactamente 10 minutos de vigencia (ADR-003)")
    void debeCrearReservaCon10MinutosVigencia() {
        assertNotNull(reserva.getId());
        assertEquals("VJ-100", reserva.getViajeId());
        assertEquals("S-05", reserva.getSillaId());
        assertEquals("PASAJERO-999", reserva.getPasajeroId());
        assertEquals(EstadoReserva.ACTIVA, reserva.getEstado());
        assertEquals(ahora, reserva.getFechaCreacion());
        assertEquals(ahora.plusMinutes(10), reserva.getFechaExpiracion());
        assertNull(reserva.getFechaConfirmacion());
        assertEquals("VJ-100:S-05", reserva.llaveActiva());
    }

    @Test
    @DisplayName("Debe verificar que la reserva sigue ACTIVA dentro de los 10 minutos")
    void debeEstarActivaDentroDeLos10Minutos() {
        LocalDateTime minuto7 = ahora.plusMinutes(7);
        assertTrue(reserva.estaActiva(minuto7));
        assertFalse(reserva.estaExpirada(minuto7));

        LocalDateTime minuto10Exacto = ahora.plusMinutes(10);
        assertFalse(reserva.estaActiva(minuto10Exacto));
        assertTrue(reserva.estaExpirada(minuto10Exacto));
    }

    @Test
    @DisplayName("Debe expirar exactamente al cumplir los 10 minutos")
    void debeExpirarEnElInstanteExactoDeVencimiento() {
        LocalDateTime vencimiento = ahora.plusMinutes(10);

        assertTrue(reserva.estaExpirada(vencimiento));
        assertFalse(reserva.estaActiva(vencimiento));
        assertThrows(ReservaExpiradaException.class, () -> reserva.confirmar(vencimiento));
        assertEquals(EstadoReserva.EXPIRADA, reserva.getEstado());
    }

    @Test
    @DisplayName("Debe detectar que la reserva expiró al superar los 10 minutos")
    void debeDetectarExpiracionDespuesDe10Minutos() {
        LocalDateTime minuto10Con1Segundo = ahora.plusMinutes(10).plusSeconds(1);

        assertFalse(reserva.estaActiva(minuto10Con1Segundo));
        assertTrue(reserva.estaExpirada(minuto10Con1Segundo));

        reserva.expirar(minuto10Con1Segundo);
        assertEquals(EstadoReserva.EXPIRADA, reserva.getEstado());
    }

    @Test
    @DisplayName("Debe confirmar una reserva vigente satisfactoriamente")
    void debeConfirmarReservaVigente() {
        LocalDateTime momentoPago = ahora.plusMinutes(5);

        reserva.confirmar(momentoPago);

        assertEquals(EstadoReserva.CONFIRMADA, reserva.getEstado());
        assertEquals(momentoPago, reserva.getFechaConfirmacion());
    }

    @Test
    @DisplayName("Debe lanzar ReservaExpiradaException al intentar confirmar una reserva después de 10 minutos")
    void debeLanzarExcepcionAlConfirmarReservaExpirada() {
        LocalDateTime momentoPagoTardio = ahora.plusMinutes(10).plusSeconds(10);

        ReservaExpiradaException ex = assertThrows(
                ReservaExpiradaException.class,
                () -> reserva.confirmar(momentoPagoTardio)
        );

        assertTrue(ex.getMessage().contains("ha expirado"));
        assertEquals(EstadoReserva.EXPIRADA, reserva.getEstado());
    }

    @Test
    @DisplayName("Debe validar constructor y rechazar parámetros inválidos")
    void debeRechazarParametrosInvalidos() {
        assertThrows(IllegalArgumentException.class,
                () -> Reserva.crear(null, "S-01", "P-01", ahora));
        assertThrows(IllegalArgumentException.class,
                () -> Reserva.crear("VJ-01", "", "P-01", ahora));
        assertThrows(IllegalArgumentException.class,
                () -> Reserva.crear("VJ-01", "S-01", "  ", ahora));
        assertThrows(IllegalArgumentException.class,
                () -> Reserva.crear("VJ-01", "S-01", "P-01", null));

        // Fecha expiración anterior o igual a fecha creación
        assertThrows(IllegalArgumentException.class,
                () -> new Reserva("R1", "VJ-01", "S-01", "P-01", EstadoReserva.ACTIVA, ahora, ahora.minusMinutes(1), null));
    }
}
