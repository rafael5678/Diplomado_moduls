package co.edu.ucc.cooperativa.reservas.infraestructura.salida.mysql;

import co.edu.ucc.cooperativa.reservas.dominio.EstadoReserva;
import co.edu.ucc.cooperativa.reservas.dominio.Reserva;
import co.edu.ucc.cooperativa.reservas.dominio.SillaNoDisponibleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Adaptador MySQL / JPA de reservas")
class ReservaMySqlAdapterTest {

    private static final String RESERVA_ID = "RES-001";
    private static final String VIAJE_ID = "VJ-COOP-01";
    private static final String SILLA_ID = "S-15";
    private static final String PASAJERO_ID = "CC-1085123456";
    private static final LocalDateTime CREACION = LocalDateTime.of(2026, 10, 23, 10, 0);
    private static final LocalDateTime EXPIRACION = CREACION.plusMinutes(10);

    @Mock
    private MySqlReservaRepository jpaRepository;

    private ReservaMySqlAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new ReservaMySqlAdapter(jpaRepository);
    }

    @Test
    @DisplayName("Guardar mapea todos los campos del dominio a la entidad JPA y de vuelta")
    void guardarMapeaDominioYEntidad() {
        Reserva reserva = new Reserva(RESERVA_ID, VIAJE_ID, SILLA_ID, PASAJERO_ID,
                EstadoReserva.CONFIRMADA, CREACION, EXPIRACION, EXPIRACION.minusMinutes(2));
        when(jpaRepository.save(any(ReservaJpaEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Reserva resultado = adapter.guardar(reserva);

        ArgumentCaptor<ReservaJpaEntity> captor = ArgumentCaptor.forClass(ReservaJpaEntity.class);
        verify(jpaRepository).save(captor.capture());
        ReservaJpaEntity entidad = captor.getValue();
        assertEquals(RESERVA_ID, entidad.getId());
        assertEquals(VIAJE_ID, entidad.getViajeId());
        assertEquals(SILLA_ID, entidad.getSillaId());
        assertEquals(PASAJERO_ID, entidad.getPasajeroId());
        assertEquals("CONFIRMADA", entidad.getEstado());
        assertTrue(entidad.isOcupaSilla());

        assertEquals(RESERVA_ID, resultado.getId());
        assertEquals(EstadoReserva.CONFIRMADA, resultado.getEstado());
    }

    @Test
    @DisplayName("Adquirir reserva libera vencidas y guarda exitosamente si la silla está libre")
    void adquirirReservaExitoso() {
        Reserva reserva = Reserva.crear(VIAJE_ID, SILLA_ID, PASAJERO_ID, CREACION);
        when(jpaRepository.contarReservasVigentes(VIAJE_ID, SILLA_ID, CREACION)).thenReturn(0L);
        when(jpaRepository.saveAndFlush(any(ReservaJpaEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Reserva resultado = adapter.adquirirReserva(reserva, CREACION);

        verify(jpaRepository).liberarReservasActivasVencidas(VIAJE_ID, SILLA_ID, CREACION);
        verify(jpaRepository).contarReservasVigentes(VIAJE_ID, SILLA_ID, CREACION);
        verify(jpaRepository).saveAndFlush(any(ReservaJpaEntity.class));
        assertNotNull(resultado);
        assertEquals(EstadoReserva.ACTIVA, resultado.getEstado());
    }

    @Test
    @DisplayName("Adquirir reserva lanza SillaNoDisponibleException si ya existe reserva vigente")
    void adquirirReservaLanzaExcepcionSiEstaOcupada() {
        Reserva reserva = Reserva.crear(VIAJE_ID, SILLA_ID, PASAJERO_ID, CREACION);
        when(jpaRepository.contarReservasVigentes(VIAJE_ID, SILLA_ID, CREACION)).thenReturn(1L);

        assertThrows(SillaNoDisponibleException.class, () -> adapter.adquirirReserva(reserva, CREACION));
        verify(jpaRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Adquirir reserva mapea DataIntegrityViolationException a SillaNoDisponibleException")
    void adquirirReservaMapeaViolacionDeIntegridad() {
        Reserva reserva = Reserva.crear(VIAJE_ID, SILLA_ID, PASAJERO_ID, CREACION);
        when(jpaRepository.contarReservasVigentes(VIAJE_ID, SILLA_ID, CREACION)).thenReturn(0L);
        when(jpaRepository.saveAndFlush(any(ReservaJpaEntity.class)))
                .thenThrow(new DataIntegrityViolationException("Duplicate key"));

        assertThrows(SillaNoDisponibleException.class, () -> adapter.adquirirReserva(reserva, CREACION));
    }

    @Test
    @DisplayName("Confirmar reserva si vigente devuelve Optional con reserva confirmada")
    void confirmarSiVigenteExitoso() {
        when(jpaRepository.confirmarReservaActivaVigente(RESERVA_ID, CREACION)).thenReturn(1);
        ReservaJpaEntity entidad = new ReservaJpaEntity(RESERVA_ID, VIAJE_ID, SILLA_ID, PASAJERO_ID,
                "CONFIRMADA", true, CREACION, EXPIRACION, CREACION);
        when(jpaRepository.findById(RESERVA_ID)).thenReturn(Optional.of(entidad));

        Optional<Reserva> opt = adapter.confirmarSiVigente(RESERVA_ID, CREACION);

        assertTrue(opt.isPresent());
        assertEquals(EstadoReserva.CONFIRMADA, opt.get().getEstado());
    }

    @Test
    @DisplayName("Buscar por id retorna vacío si id es nulo o en blanco")
    void buscarPorIdRetornaVacioConEntradaInvalida() {
        assertTrue(adapter.buscarPorId(null).isEmpty());
        assertTrue(adapter.buscarPorId("   ").isEmpty());
        verify(jpaRepository, never()).findById(any());
    }

    @Test
    @DisplayName("Buscar reservas expiradas delega al repositorio JPA")
    void buscarReservasExpiradasDelega() {
        ReservaJpaEntity entidad = new ReservaJpaEntity(RESERVA_ID, VIAJE_ID, SILLA_ID, PASAJERO_ID,
                "ACTIVA", true, CREACION, EXPIRACION, null);
        when(jpaRepository.findByEstadoAndFechaExpiracionLessThanEqual("ACTIVA", EXPIRACION))
                .thenReturn(List.of(entidad));

        List<Reserva> expiradas = adapter.buscarReservasExpiradas(EXPIRACION);

        assertEquals(1, expiradas.size());
        assertEquals(RESERVA_ID, expiradas.get(0).getId());
    }
}
