package co.edu.ucc.cooperativa.reservas.infraestructura.salida.mongodb;

import co.edu.ucc.cooperativa.reservas.dominio.EstadoReserva;
import co.edu.ucc.cooperativa.reservas.dominio.Reserva;
import co.edu.ucc.cooperativa.reservas.dominio.SillaNoDisponibleException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.MongoPersistentEntityIndexResolver;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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
        verificarSinOcupacionesDuplicadasAntesDeIndexar();
        crearIndicesDeclaradosEnElDocumento();
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

        Reserva guardada = guardar(esperada);
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
        guardar("viaje-2", "silla-11", EstadoReserva.CONFIRMADA, EXPIRACION.minusMinutes(1));

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
        guardar(vigenteDespues);

        List<Reserva> vencidas = adapter.buscarReservasExpiradas(EXPIRACION);

        assertTrue(vencidas.stream().anyMatch(r -> r.getId().equals(vencidaExactamenteAhora.getId())));
        assertFalse(vencidas.stream().anyMatch(r -> r.getId().equals(vigenteDespues.getId())));
    }

    @Test
    @DisplayName("El índice parcial único existe y rechaza una segunda ocupación del mismo viaje y silla")
    void indiceParcialUnicoEsEfectivo() {
        org.bson.Document ocupacion = mongoTemplate.getCollection("reservas").listIndexes()
                .into(new ArrayList<>()).stream()
                .filter(info -> "ux_coop_viaje_silla_ocupada".equals(info.getString("name")))
                .findFirst().orElseThrow();
        assertEquals(new org.bson.Document("viajeId", 1).append("sillaId", 1), ocupacion.get("key"));
        assertEquals(Boolean.TRUE, ocupacion.getBoolean("unique"));
        assertEquals(new org.bson.Document("$or", List.of(
                        new org.bson.Document("ocupaSilla", true),
                        new org.bson.Document("estado", new org.bson.Document("$in",
                                List.of("ACTIVA", "CONFIRMADA")))
                )), ocupacion.get("partialFilterExpression"));

        LocalDateTime ahora = CREACION;
        Reserva ocupante = reserva("it-index-viaje", "silla-1", EstadoReserva.ACTIVA, null);
        guardar(ocupante);
        Reserva conflicto = reserva("it-index-viaje", "silla-1", EstadoReserva.ACTIVA, null);
        registrarParaLimpieza(conflicto);

        assertThrows(SillaNoDisponibleException.class, () -> adapter.adquirirReserva(conflicto, ahora));
    }

    @Test
    @DisplayName("Dos adquisiciones simultáneas de la misma silla producen exactamente un ganador")
    void adquisicionesConcurrentesTienenUnSoloGanador() throws Exception {
        String viaje = "it-concurrente-" + UUID.randomUUID();
        LocalDateTime ahora = CREACION;
        Reserva primera = new Reserva("it-" + UUID.randomUUID(), viaje, "silla-1", "pasajero-1",
                EstadoReserva.ACTIVA, ahora, ahora.plusMinutes(10), null);
        Reserva segunda = new Reserva("it-" + UUID.randomUUID(), viaje, "silla-1", "pasajero-2",
                EstadoReserva.ACTIVA, ahora, ahora.plusMinutes(10), null);
        registrarParaLimpieza(primera);
        registrarParaLimpieza(segunda);

        CyclicBarrier barrera = new CyclicBarrier(2);
        ExecutorService ejecutor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> resultadoPrimera = ejecutor.submit(() -> adquirirTrasBarrera(barrera, primera, ahora));
            Future<Boolean> resultadoSegunda = ejecutor.submit(() -> adquirirTrasBarrera(barrera, segunda, ahora));

            int exitosas = (resultadoPrimera.get(15, TimeUnit.SECONDS) ? 1 : 0)
                    + (resultadoSegunda.get(15, TimeUnit.SECONDS) ? 1 : 0);
            assertEquals(1, exitosas);
            assertEquals(1, repository.countByViajeIdAndSillaIdAndOcupaSillaTrue(viaje, "silla-1"));
        } finally {
            ejecutor.shutdownNow();
            ejecutor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    @DisplayName("Una nueva reserva en el instante de vencimiento libera sin borrar el historial")
    void nuevaReservaEnLimiteExpiraLaAnteriorYConservaHistorial() {
        LocalDateTime ahora = EXPIRACION;
        Reserva vencida = new Reserva("it-" + UUID.randomUUID(), "it-expiracion", "silla-2", "pasajero-viejo",
                EstadoReserva.ACTIVA, CREACION, ahora, null);
        guardar(vencida);
        Reserva nueva = new Reserva("it-" + UUID.randomUUID(), "it-expiracion", "silla-2", "pasajero-nuevo",
                EstadoReserva.ACTIVA, ahora, ahora.plusMinutes(10), null);
        registrarParaLimpieza(nueva);

        Reserva adquirida = adapter.adquirirReserva(nueva, ahora);

        ReservaDocument anteriorPersistida = repository.findById(vencida.getId()).orElseThrow();
        assertEquals(EstadoReserva.EXPIRADA.name(), anteriorPersistida.getEstado());
        assertFalse(anteriorPersistida.isOcupaSilla());
        assertTrue(repository.findById(vencida.getId()).isPresent());
        assertEquals(nueva.getId(), adquirida.getId());
        assertTrue(repository.findById(nueva.getId()).orElseThrow().isOcupaSilla());
    }

    @Test
    @DisplayName("Una confirmada no se libera aunque su expiración original haya pasado")
    void reservaConfirmadaVencidaSigueOcupando() {
        LocalDateTime ahora = EXPIRACION.plusDays(1);
        Reserva confirmada = new Reserva("it-" + UUID.randomUUID(), "it-confirmada", "silla-3", "pasajero-confirmado",
                EstadoReserva.CONFIRMADA, CREACION, EXPIRACION, EXPIRACION.minusMinutes(1));
        guardar(confirmada);
        Reserva nueva = new Reserva("it-" + UUID.randomUUID(), "it-confirmada", "silla-3", "otro-pasajero",
                EstadoReserva.ACTIVA, ahora, ahora.plusMinutes(10), null);
        registrarParaLimpieza(nueva);

        assertThrows(SillaNoDisponibleException.class, () -> adapter.adquirirReserva(nueva, ahora));
        ReservaDocument persistida = repository.findById(confirmada.getId()).orElseThrow();
        assertEquals(EstadoReserva.CONFIRMADA.name(), persistida.getEstado());
        assertTrue(persistida.isOcupaSilla());
    }

    @Test
    @DisplayName("Un documento confirmado histórico sin ocupaSilla también queda protegido")
    void confirmadaHistoricaSinIndicadorSigueBloqueando() {
        String id = "it-legacy-" + UUID.randomUUID();
        idsCreadosPorEstaPrueba.add(id);
        mongoTemplate.getCollection("reservas").insertOne(new org.bson.Document("_id", id)
                .append("viajeId", "it-legacy-viaje")
                .append("sillaId", "silla-4")
                .append("pasajeroId", "pasajero-legacy")
                .append("estado", EstadoReserva.CONFIRMADA.name())
                .append("fechaCreacion", java.util.Date.from(CREACION.atZone(ZoneId.systemDefault()).toInstant()))
                .append("fechaExpiracion", java.util.Date.from(EXPIRACION.atZone(ZoneId.systemDefault()).toInstant())));
        Reserva nueva = new Reserva("it-" + UUID.randomUUID(), "it-legacy-viaje", "silla-4", "otro-pasajero",
                EstadoReserva.ACTIVA, EXPIRACION, EXPIRACION.plusMinutes(10), null);
        registrarParaLimpieza(nueva);

        assertThrows(SillaNoDisponibleException.class, () -> adapter.adquirirReserva(nueva, EXPIRACION));
        assertTrue(repository.findById(id).isPresent());
    }

    @Test
    @DisplayName("Una reserva activa histórica sin ocupaSilla bloquea hasta vencer y luego conserva historial")
    void activaHistoricaSinIndicadorSeLiberaAlVencer() {
        String id = "it-legacy-activa-" + UUID.randomUUID();
        idsCreadosPorEstaPrueba.add(id);
        mongoTemplate.getCollection("reservas").insertOne(new org.bson.Document("_id", id)
                .append("viajeId", "it-legacy-activa-viaje")
                .append("sillaId", "silla-6")
                .append("pasajeroId", "pasajero-legacy")
                .append("estado", EstadoReserva.ACTIVA.name())
                .append("fechaCreacion", java.util.Date.from(CREACION.atZone(ZoneId.systemDefault()).toInstant()))
                .append("fechaExpiracion", java.util.Date.from(EXPIRACION.atZone(ZoneId.systemDefault()).toInstant())));

        Reserva nueva = new Reserva("it-" + UUID.randomUUID(), "it-legacy-activa-viaje", "silla-6",
                "pasajero-nuevo", EstadoReserva.ACTIVA, CREACION, EXPIRACION.plusMinutes(10), null);
        registrarParaLimpieza(nueva);

        assertThrows(SillaNoDisponibleException.class,
                () -> adapter.adquirirReserva(nueva, EXPIRACION.minusSeconds(1)));
        assertTrue(repository.findById(id).isPresent());

        Reserva adquirida = adapter.adquirirReserva(nueva, EXPIRACION);

        ReservaDocument anteriorPersistida = repository.findById(id).orElseThrow();
        assertEquals(EstadoReserva.EXPIRADA.name(), anteriorPersistida.getEstado());
        assertFalse(anteriorPersistida.isOcupaSilla());
        assertTrue(repository.findById(id).isPresent());
        assertEquals(nueva.getId(), adquirida.getId());
        assertTrue(repository.findById(nueva.getId()).orElseThrow().isOcupaSilla());
    }

    @Test
    @DisplayName("La misma silla se puede adquirir en viajes distintos")
    void mismaSillaEnViajesDistintos() {
        LocalDateTime ahora = CREACION;
        Reserva primerViaje = new Reserva("it-" + UUID.randomUUID(), "it-viaje-a", "silla-5", "pasajero-a",
                EstadoReserva.ACTIVA, ahora, ahora.plusMinutes(10), null);
        Reserva segundoViaje = new Reserva("it-" + UUID.randomUUID(), "it-viaje-b", "silla-5", "pasajero-b",
                EstadoReserva.ACTIVA, ahora, ahora.plusMinutes(10), null);
        registrarParaLimpieza(primerViaje);
        registrarParaLimpieza(segundoViaje);

        assertEquals(primerViaje.getId(), adapter.adquirirReserva(primerViaje, ahora).getId());
        assertEquals(segundoViaje.getId(), adapter.adquirirReserva(segundoViaje, ahora).getId());
    }

    private Reserva guardar(String viaje, String silla, EstadoReserva estado, LocalDateTime confirmacion) {
        Reserva reserva = reserva(viaje, silla, estado, confirmacion);
        return guardar(reserva);
    }

    private Reserva guardar(Reserva reserva) {
        // Registrar antes de escribir para que AfterEach también pueda limpiar
        // si el guardado lanza una excepción después de persistir parcialmente.
        idsCreadosPorEstaPrueba.add(reserva.getId());
        adapter.guardar(reserva);
        return reserva;
    }

    private void registrarParaLimpieza(Reserva reserva) {
        idsCreadosPorEstaPrueba.add(reserva.getId());
    }

    private boolean adquirirTrasBarrera(CyclicBarrier barrera, Reserva reserva, LocalDateTime ahora) throws Exception {
        barrera.await(10, TimeUnit.SECONDS);
        try {
            adapter.adquirirReserva(reserva, ahora);
            return true;
        } catch (SillaNoDisponibleException ocupada) {
            return false;
        }
    }

    private void verificarSinOcupacionesDuplicadasAntesDeIndexar() {
        List<org.bson.Document> duplicados = mongoTemplate.getCollection("reservas").aggregate(List.of(
                new org.bson.Document("$match", new org.bson.Document("$or", List.of(
                        new org.bson.Document("ocupaSilla", true),
                        new org.bson.Document("estado", new org.bson.Document("$in",
                                List.of("ACTIVA", "CONFIRMADA")))
                ))),
                new org.bson.Document("$group", new org.bson.Document("_id", new org.bson.Document("viajeId", "$viajeId")
                        .append("sillaId", "$sillaId")).append("cantidad", new org.bson.Document("$sum", 1))
                        .append("ids", new org.bson.Document("$push", "$_id"))),
                new org.bson.Document("$match", new org.bson.Document("cantidad", new org.bson.Document("$gt", 1)))
        )).into(new ArrayList<>());
        assertTrue(duplicados.isEmpty(), "No se puede garantizar exclusividad: ocupaciones duplicadas " + duplicados);
    }

    private void crearIndicesDeclaradosEnElDocumento() {
        MongoPersistentEntityIndexResolver resolver = new MongoPersistentEntityIndexResolver(
                mongoTemplate.getConverter().getMappingContext());
        var indexOperations = mongoTemplate.indexOps(ReservaDocument.class);
        resolver.resolveIndexFor(ReservaDocument.class).forEach(indexOperations::ensureIndex);
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
