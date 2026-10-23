package co.edu.ucc.cooperativa.reservas.infraestructura.salida.mysql;

import co.edu.ucc.cooperativa.reservas.dominio.EstadoReserva;
import co.edu.ucc.cooperativa.reservas.dominio.Reserva;
import co.edu.ucc.cooperativa.reservas.dominio.SillaNoDisponibleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@Import(ReservaMySqlAdapter.class)
@ActiveProfiles("mysql")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@DisplayName("Pruebas de Integración JPA del Adaptador MySQL (en memoria H2 modo MySQL)")
class ReservaMySqlAdapterIT {

    private static final String VIAJE_ID = "VJ-COOP-01";
    private static final String SILLA_ID = "S-15";
    private static final String PASAJERO_ID = "CC-1085123456";
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 23, 10, 0);

    @Autowired
    private ReservaMySqlAdapter adapter;

    @Autowired
    private MySqlReservaRepository repository;

    @Test
    @DisplayName("Debe persistir y recuperar una reserva con estado ACTIVA")
    void debePersistirYRecuperarReserva() {
        Reserva reserva = Reserva.crear(VIAJE_ID, SILLA_ID, PASAJERO_ID, AHORA);

        Reserva guardada = adapter.adquirirReserva(reserva, AHORA);

        assertNotNull(guardada.getId());
        Optional<Reserva> consultada = adapter.buscarPorId(guardada.getId());
        assertTrue(consultada.isPresent());
        assertEquals(VIAJE_ID, consultada.get().getViajeId());
        assertEquals(SILLA_ID, consultada.get().getSillaId());
        assertEquals(EstadoReserva.ACTIVA, consultada.get().getEstado());
    }

    @Test
    @DisplayName("No debe permitir adquirir la misma silla si ya existe una reserva activa vigente")
    void noPermiteDobleReservaActiva() {
        Reserva reserva1 = Reserva.crear(VIAJE_ID, SILLA_ID, PASAJERO_ID, AHORA);
        adapter.adquirirReserva(reserva1, AHORA);

        Reserva reserva2 = Reserva.crear(VIAJE_ID, SILLA_ID, "CC-9999999999", AHORA.plusMinutes(2));

        assertThrows(SillaNoDisponibleException.class, () -> adapter.adquirirReserva(reserva2, AHORA.plusMinutes(2)));
    }

    @Test
    @DisplayName("Debe permitir adquirir la silla si la reserva previa ya expiró")
    void permiteAdquirirSiReservaPreviaExpiro() {
        Reserva reserva1 = Reserva.crear(VIAJE_ID, SILLA_ID, PASAJERO_ID, AHORA);
        adapter.adquirirReserva(reserva1, AHORA);

        // A los 11 minutos, la primera reserva ya superó su ventana de 10 min
        LocalDateTime despuesDeExpirar = AHORA.plusMinutes(11);
        Reserva reserva2 = Reserva.crear(VIAJE_ID, SILLA_ID, "CC-9999999999", despuesDeExpirar);

        Reserva adquirida = adapter.adquirirReserva(reserva2, despuesDeExpirar);
        assertNotNull(adquirida);
        assertEquals("CC-9999999999", adquirida.getPasajeroId());
    }

    @Test
    @DisplayName("Debe confirmar una reserva activa vigente exitosamente")
    void debeConfirmarReservaVigente() {
        Reserva reserva = Reserva.crear(VIAJE_ID, SILLA_ID, PASAJERO_ID, AHORA);
        Reserva creada = adapter.adquirirReserva(reserva, AHORA);

        LocalDateTime tiempoConfirmacion = AHORA.plusMinutes(5);
        Optional<Reserva> confirmada = adapter.confirmarSiVigente(creada.getId(), tiempoConfirmacion);

        assertTrue(confirmada.isPresent());
        assertEquals(EstadoReserva.CONFIRMADA, confirmada.get().getEstado());
        assertTrue(adapter.existeReservaConfirmada(VIAJE_ID, SILLA_ID));
    }
}
