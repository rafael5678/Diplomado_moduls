package co.edu.ucc.cooperativa.reservas.infraestructura.salida.mongodb;

import co.edu.ucc.cooperativa.reservas.dominio.EstadoReserva;
import co.edu.ucc.cooperativa.reservas.dominio.Reserva;
import co.edu.ucc.cooperativa.reservas.dominio.SillaNoDisponibleException;
import org.mockito.InOrder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.dao.DuplicateKeyException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Adaptador Mongo de reservas")
class ReservaMongoAdapterTest {

    private static final String RESERVA_ID = "RES-001";
    private static final String VIAJE_ID = "VJ-COOP-01";
    private static final String SILLA_ID = "S-15";
    private static final String PASAJERO_ID = "CC-1085123456";
    private static final LocalDateTime CREACION = LocalDateTime.of(2026, 10, 23, 10, 0);
    private static final LocalDateTime EXPIRACION = CREACION.plusMinutes(10);

    @Mock
    private MongoReservaRepository mongoRepository;

    private ReservaMongoAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new ReservaMongoAdapter(mongoRepository);
    }

    @Test
    @DisplayName("Guardar mapea todos los campos del dominio al documento y de vuelta")
    void guardarMapeaDominioYDocumento() {
        Reserva reserva = new Reserva(RESERVA_ID, VIAJE_ID, SILLA_ID, PASAJERO_ID,
                EstadoReserva.CONFIRMADA, CREACION, EXPIRACION, EXPIRACION.minusMinutes(2));
        when(mongoRepository.save(any(ReservaDocument.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Reserva resultado = adapter.guardar(reserva);

        ArgumentCaptor<ReservaDocument> captor = ArgumentCaptor.forClass(ReservaDocument.class);
        verify(mongoRepository).save(captor.capture());
        ReservaDocument documento = captor.getValue();
        assertEquals(RESERVA_ID, documento.getId());
        assertEquals(VIAJE_ID, documento.getViajeId());
        assertEquals(SILLA_ID, documento.getSillaId());
        assertEquals(PASAJERO_ID, documento.getPasajeroId());
        assertEquals(EstadoReserva.CONFIRMADA.name(), documento.getEstado());
        assertTrue(documento.isOcupaSilla());
        assertEquals(CREACION, documento.getFechaCreacion());
        assertEquals(EXPIRACION, documento.getFechaExpiracion());
        assertEquals(EXPIRACION.minusMinutes(2), documento.getFechaConfirmacion());
        assertDomain(reserva, resultado);
    }

    @Test
    @DisplayName("Adquirir libera solo vencidas y luego inserta una reserva ocupante")
    void adquirirLiberaVencidasEInsertaNuevaReserva() {
        LocalDateTime ahora = EXPIRACION;
        Reserva nueva = Reserva.crear(VIAJE_ID, SILLA_ID, PASAJERO_ID, ahora);
        when(mongoRepository.insert(any(ReservaDocument.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Reserva resultado = adapter.adquirirReserva(nueva, ahora);

        InOrder orden = inOrder(mongoRepository);
        orden.verify(mongoRepository).liberarReservasActivasVencidas(VIAJE_ID, SILLA_ID, ahora);
        ArgumentCaptor<ReservaDocument> captor = ArgumentCaptor.forClass(ReservaDocument.class);
        orden.verify(mongoRepository).insert(captor.capture());
        assertTrue(captor.getValue().isOcupaSilla());
        assertEquals(nueva.getId(), resultado.getId());
    }

    @Test
    @DisplayName("Traduce duplicado de índice a silla no disponible")
    void adquirirTraduceClaveDuplicada() {
        LocalDateTime ahora = CREACION;
        Reserva nueva = Reserva.crear(VIAJE_ID, SILLA_ID, PASAJERO_ID, ahora);
        when(mongoRepository.insert(any(ReservaDocument.class)))
                .thenThrow(new DuplicateKeyException("duplicate key"));

        assertThrows(SillaNoDisponibleException.class, () -> adapter.adquirirReserva(nueva, ahora));
        verify(mongoRepository).liberarReservasActivasVencidas(VIAJE_ID, SILLA_ID, ahora);
        verify(mongoRepository).insert(any(ReservaDocument.class));
    }

    @Test
    @DisplayName("La confirmación en persistencia requiere ACTIVA y fecha de expiración futura")
    void confirmarSiVigenteUsaActualizacionCondicional() {
        when(mongoRepository.confirmarReservaActivaVigente(RESERVA_ID, EXPIRACION.minusSeconds(1)))
                .thenReturn(1L);
        when(mongoRepository.findById(RESERVA_ID)).thenReturn(Optional.of(documento(EstadoReserva.CONFIRMADA)));

        Optional<Reserva> resultado = adapter.confirmarSiVigente(RESERVA_ID, EXPIRACION.minusSeconds(1));

        assertEquals(EstadoReserva.CONFIRMADA, resultado.orElseThrow().getEstado());
        verify(mongoRepository).confirmarReservaActivaVigente(RESERVA_ID, EXPIRACION.minusSeconds(1));
    }

    @Test
    @DisplayName("Buscar por ID mapea un documento a la entidad de dominio")
    void buscarPorIdMapeaDocumentoADominio() {
        when(mongoRepository.findById(RESERVA_ID)).thenReturn(Optional.of(documento(EstadoReserva.ACTIVA)));

        Optional<Reserva> resultado = adapter.buscarPorId(RESERVA_ID);

        assertTrue(resultado.isPresent());
        assertDomain(documentoADominio(documento(EstadoReserva.ACTIVA)), resultado.orElseThrow());
        verify(mongoRepository).findById(RESERVA_ID);
    }

    @Test
    @DisplayName("Buscar reserva activa consulta ACTIVA con expiración posterior a la hora indicada")
    void buscarReservaActivaUsaEstadoYHoraActual() {
        LocalDateTime ahora = EXPIRACION.minusSeconds(1);
        when(mongoRepository.findFirstByViajeIdAndSillaIdAndEstadoAndFechaExpiracionAfter(
                VIAJE_ID, SILLA_ID, EstadoReserva.ACTIVA.name(), ahora))
                .thenReturn(Optional.of(documento(EstadoReserva.ACTIVA)));

        Optional<Reserva> resultado = adapter.buscarReservaActiva(VIAJE_ID, SILLA_ID, ahora);

        assertTrue(resultado.isPresent());
        assertEquals(EstadoReserva.ACTIVA, resultado.orElseThrow().getEstado());
        verify(mongoRepository).findFirstByViajeIdAndSillaIdAndEstadoAndFechaExpiracionAfter(
                VIAJE_ID, SILLA_ID, EstadoReserva.ACTIVA.name(), ahora);
    }

    @Test
    @DisplayName("La consulta de reserva activa pasa la igualdad del vencimiento al criterio After")
    void reservaActivaUsaLimiteEstrictoEnVencimiento() {
        when(mongoRepository.existsByViajeIdAndSillaIdAndEstadoAndFechaExpiracionAfter(
                VIAJE_ID, SILLA_ID, EstadoReserva.ACTIVA.name(), EXPIRACION)).thenReturn(false);

        boolean existe = adapter.existeReservaActiva(VIAJE_ID, SILLA_ID, EXPIRACION);

        assertFalse(existe);
        verify(mongoRepository).existsByViajeIdAndSillaIdAndEstadoAndFechaExpiracionAfter(
                VIAJE_ID, SILLA_ID, EstadoReserva.ACTIVA.name(), EXPIRACION);
    }

    @Test
    @DisplayName("La búsqueda de confirmadas usa el mismo viaje y silla y estado CONFIRMADA")
    void buscaReservasConfirmadas() {
        when(mongoRepository.existsByViajeIdAndSillaIdAndEstado(
                VIAJE_ID, SILLA_ID, EstadoReserva.CONFIRMADA.name())).thenReturn(true);

        assertTrue(adapter.existeReservaConfirmada(VIAJE_ID, SILLA_ID));

        verify(mongoRepository).existsByViajeIdAndSillaIdAndEstado(
                VIAJE_ID, SILLA_ID, EstadoReserva.CONFIRMADA.name());
    }

    @Test
    @DisplayName("La búsqueda de expiradas incluye la igualdad exacta de fecha de expiración")
    void buscaReservasExpiradasIncluyendoElInstanteExacto() {
        ReservaDocument vencidaEnElLimite = documento(EstadoReserva.ACTIVA);
        when(mongoRepository.findByEstadoAndFechaExpiracionLessThanEqual(
                EstadoReserva.ACTIVA.name(), EXPIRACION)).thenReturn(List.of(vencidaEnElLimite));

        List<Reserva> resultado = adapter.buscarReservasExpiradas(EXPIRACION);

        assertEquals(1, resultado.size());
        assertEquals(EstadoReserva.ACTIVA, resultado.get(0).getEstado());
        assertEquals(EXPIRACION, resultado.get(0).getFechaExpiracion());
        verify(mongoRepository).findByEstadoAndFechaExpiracionLessThanEqual(
                EstadoReserva.ACTIVA.name(), EXPIRACION);
    }

    @Test
    @DisplayName("Buscar por ID vacío no consulta Mongo")
    void buscarPorIdVacioDevuelveVacioSinConsultarMongo() {
        assertTrue(adapter.buscarPorId("  ").isEmpty());
        verifyNoInteractions(mongoRepository);
    }

    private static ReservaDocument documento(EstadoReserva estado) {
        return new ReservaDocument(RESERVA_ID, VIAJE_ID, SILLA_ID, PASAJERO_ID,
                estado.name(), CREACION, EXPIRACION,
                estado == EstadoReserva.CONFIRMADA ? EXPIRACION.minusMinutes(2) : null);
    }

    private static Reserva documentoADominio(ReservaDocument documento) {
        return new Reserva(documento.getId(), documento.getViajeId(), documento.getSillaId(),
                documento.getPasajeroId(), EstadoReserva.valueOf(documento.getEstado()),
                documento.getFechaCreacion(), documento.getFechaExpiracion(),
                documento.getFechaConfirmacion());
    }

    private static void assertDomain(Reserva esperado, Reserva actual) {
        assertAll(
                () -> assertEquals(esperado.getId(), actual.getId()),
                () -> assertEquals(esperado.getViajeId(), actual.getViajeId()),
                () -> assertEquals(esperado.getSillaId(), actual.getSillaId()),
                () -> assertEquals(esperado.getPasajeroId(), actual.getPasajeroId()),
                () -> assertEquals(esperado.getEstado(), actual.getEstado()),
                () -> assertEquals(esperado.getFechaCreacion(), actual.getFechaCreacion()),
                () -> assertEquals(esperado.getFechaExpiracion(), actual.getFechaExpiracion()),
                () -> assertEquals(esperado.getFechaConfirmacion(), actual.getFechaConfirmacion())
        );
    }
}
