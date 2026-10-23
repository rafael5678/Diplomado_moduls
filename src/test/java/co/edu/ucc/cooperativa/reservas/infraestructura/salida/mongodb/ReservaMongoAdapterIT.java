package co.edu.ucc.cooperativa.reservas.infraestructura.salida.mongodb;

import co.edu.ucc.cooperativa.reservas.dominio.EstadoReserva;
import co.edu.ucc.cooperativa.reservas.dominio.Reserva;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas de integración con MongoDB real. Se ejecutan aparte del conjunto unitario
 * mediante: mvn -Dtest=ReservaMongoAdapterIT test
 */
@DataMongoTest
@Import(ReservaMongoAdapter.class)
@TestPropertySource(properties = {
        "spring.mongodb.uri=mongodb://localhost:27018/cooperativa_reservas_it",
        "spring.data.mongodb.auto-index-creation=false"
})
@DisplayName("Integración real del adaptador MongoDB de reservas")
class ReservaMongoAdapterIT {

    private static final String BASE_DE_PRUEBAS = "cooperativa_reservas_it";
    private static final LocalDateTime CREACION = LocalDateTime.of(2026, 10, 23, 10, 0);
    private static final LocalDateTime EXPIRACION = CREACION.plusMinutes(10);

    @Autowired
    private ReservaMongoAdapter adapter;

    @Autowired
    private MongoReservaRepository repository;

    @Autowired
    private MongoTemplate mongoTemplate;

    private final List<String> idsCreadosPorEstaPrueba = new ArrayList<>();

    @BeforeEach
    void verificarBaseAntesDeUsarla() {
        verificarBaseSegura();
    }

    @AfterEach
    void limpiarSoloDocumentosCreadosPorEstaPrueba() {
        verificarBaseSegura();
        if (!idsCreadosPorEstaPrueba.isEmpty()) {
            repository.deleteAllById(List.copyOf(idsCreadosPorEstaPrueba));
            idsCreadosPorEstaPrueba.clear();
        }
    }

    @Test
    @DisplayName("Guarda y recupera una reserva con todos sus campos mapeados")
    void guardaRecuperaYMapeaReserva() {
        Reserva esperada = reserva("viaje-1", "silla-8", EstadoReserva.CONFIRMADA,
                EXPIRACION.minusMinutes(2));

        Reserva guardada = adapter.guardar(esperada);
        Reserva recuperada = adapter.buscarPorId(esperada.getId()).orElseThrow();

        assertReservaIgual(esperada, guardada);
        assertReservaIgual(esperada, recuperada);
        ReservaDocument documento = repository.findById(esperada.getId()).orElseThrow();
        assertAll(
                () -> assertEquals(esperada.getViajeId(), documento.getViajeId()),
                () -> assertEquals(esperada.getSillaId(), documento.getSillaId()),
                () -> assertEquals(esperada.getPasajeroId(), documento.getPasajeroId()),
                () -> assertEquals(EstadoReserva.CONFIRMADA.name(), documento.getEstado()),
                () -> assertEquals(esperada.getFechaCreacion(), documento.getFechaCreacion()),
                () -> assertEquals(esperada.getFechaExpiracion(), documento.getFechaExpiracion()),
                () -> assertEquals(esperada.getFechaConfirmacion(), documento.getFechaConfirmacion())
        );
    }

    @Test
    @DisplayName("Consulta por viaje, silla y estado activo")
    void consultaReservaActivaPorViajeSillaYEstado() {
        Reserva activa = guardar("viaje-2", "silla-9", EstadoReserva.ACTIVA, null);
        guardar("otro-viaje", "silla-9", EstadoReserva.ACTIVA, null);
        guardar("viaje-2", "silla-10", EstadoReserva.ACTIVA, null);
        guardar("viaje-2", "silla-9", EstadoReserva.CONFIRMADA, EXPIRACION.minusMinutes(1));

        assertTrue(adapter.existeReservaActiva("viaje-2", "silla-9", EXPIRACION.minusSeconds(1)));
        assertEquals(activa.getId(), adapter.buscarReservaActiva(
                "viaje-2", "silla-9", EXPIRACION.minusSeconds(1)).orElseThrow().getId());
        assertFalse(adapter.existeReservaActiva("viaje-inexistente", "silla-9", EXPIRACION.minusSeconds(1)));
        assertFalse(adapter.existeReservaActiva("viaje-2", "silla-inexistente", EXPIRACION.minusSeconds(1)));
    }

    @Test
    @DisplayName("Una reserva activa está vigente antes de expirar y vence en la igualdad exacta")
    void manejaLimiteExactoDeExpiracion() {
        Reserva activa = guardar("viaje-3", "silla-1", EstadoReserva.ACTIVA, null);

        assertTrue(adapter.existeReservaActiva("viaje-3", "silla-1", EXPIRACION.minusSeconds(1)));
        assertTrue(adapter.buscarReservaActiva("viaje-3", "silla-1", EXPIRACION.minusSeconds(1)).isPresent());

        assertFalse(adapter.existeReservaActiva("viaje-3", "silla-1", EXPIRACION));
        assertTrue(adapter.buscarReservaActiva("viaje-3", "silla-1", EXPIRACION).isEmpty());
        List<Reserva> expiradas = adapter.buscarReservasExpiradas(EXPIRACION);
        assertTrue(expiradas.stream().anyMatch(r -> r.getId().equals(activa.getId())));
        assertEquals(EstadoReserva.ACTIVA, expiradas.stream()
                .filter(r -> r.getId().equals(activa.getId())).findFirst().orElseThrow().getEstado());
    }

    @Test
    @DisplayName("Encuentra reservas confirmadas por viaje y silla aunque su fecha haya pasado")
    void consultaReservasConfirmadas() {
        guardar("viaje-4", "silla-5", EstadoReserva.CONFIRMADA, EXPIRACION.minusMinutes(1));

        assertTrue(adapter.existeReservaConfirmada("viaje-4", "silla-5"));
        assertFalse(adapter.existeReservaConfirmada("otro-viaje", "silla-5"));
        assertFalse(adapter.existeReservaConfirmada("viaje-4", "otra-silla"));
    }

    @Test
    @DisplayName("La consulta de vencidas incluye fechaExpiracion igual al instante consultado")
    void repositorioIncluyeIgualdadEnConsultaDeExpiradas() {
        Reserva vencidaExactamenteAhora = guardar("viaje-5", "silla-2", EstadoReserva.ACTIVA, null);
        Reserva vigenteDespues = new Reserva("it-" + UUID.randomUUID(), "viaje-5", "silla-3",
                "pasajero-it", EstadoReserva.ACTIVA, CREACION, EXPIRACION.plusSeconds(1), null);
        idsCreadosPorEstaPrueba.add(vigenteDespues.getId());
        adapter.guardar(vigenteDespues);

        List<Reserva> vencidas = adapter.buscarReservasExpiradas(EXPIRACION);

        assertTrue(vencidas.stream().anyMatch(r -> r.getId().equals(vencidaExactamenteAhora.getId())));
        assertFalse(vencidas.stream().anyMatch(r -> r.getId().equals(vigenteDespues.getId())));
    }

    private Reserva guardar(String viaje, String silla, EstadoReserva estado, LocalDateTime confirmacion) {
        Reserva reserva = reserva(viaje, silla, estado, confirmacion);
        idsCreadosPorEstaPrueba.add(reserva.getId());
        adapter.guardar(reserva);
        return reserva;
    }

    private static Reserva reserva(String viaje, String silla, EstadoReserva estado,
                                   LocalDateTime confirmacion) {
        LocalDateTime fechaExpiracion = EXPIRACION;
        return new Reserva("it-" + UUID.randomUUID(), viaje, silla, "pasajero-it", estado,
                CREACION, fechaExpiracion, confirmacion);
    }

    private void verificarBaseSegura() {
        String baseEfectiva = mongoTemplate.getDb().getName();
        assertEquals(BASE_DE_PRUEBAS, baseEfectiva,
                "La suite se detiene para proteger bases distintas de " + BASE_DE_PRUEBAS);
    }

    private static void assertReservaIgual(Reserva esperada, Reserva actual) {
        assertAll(
                () -> assertEquals(esperada.getId(), actual.getId()),
                () -> assertEquals(esperada.getViajeId(), actual.getViajeId()),
                () -> assertEquals(esperada.getSillaId(), actual.getSillaId()),
                () -> assertEquals(esperada.getPasajeroId(), actual.getPasajeroId()),
                () -> assertEquals(esperada.getEstado(), actual.getEstado()),
                () -> assertEquals(esperada.getFechaCreacion(), actual.getFechaCreacion()),
                () -> assertEquals(esperada.getFechaExpiracion(), actual.getFechaExpiracion()),
                () -> assertEquals(esperada.getFechaConfirmacion(), actual.getFechaConfirmacion())
        );
    }
}
