package co.edu.ucc.cooperativa.reservas.infraestructura.salida.mysql;

import co.edu.ucc.cooperativa.reservas.aplicacion.puertos.salida.ReservaRepositoryPort;
import co.edu.ucc.cooperativa.reservas.dominio.EstadoReserva;
import co.edu.ucc.cooperativa.reservas.dominio.Reserva;
import co.edu.ucc.cooperativa.reservas.dominio.SillaNoDisponibleException;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptador Secundario / De Salida (Driven Adapter) para MySQL / JPA.
 *
 * Implementa el puerto ReservaRepositoryPort respetando la arquitectura hexagonal.
 */
@Component("cooperativaReservaMySqlAdapter")
@Profile("mysql")
@Transactional
public class ReservaMySqlAdapter implements ReservaRepositoryPort {

    private final MySqlReservaRepository jpaRepository;

    public ReservaMySqlAdapter(MySqlReservaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Reserva guardar(Reserva reserva) {
        ReservaJpaEntity entity = toEntity(reserva);
        ReservaJpaEntity guardada = jpaRepository.save(entity);
        return toDomain(guardada);
    }

    @Override
    public Reserva adquirirReserva(Reserva reserva, LocalDateTime ahora) {
        // 1. Liberar reservas activas vencidas para esta misma silla/viaje
        jpaRepository.liberarReservasActivasVencidas(reserva.getViajeId(), reserva.getSillaId(), ahora);

        // 2. Validar si existe una reserva vigente o confirmada
        long vigentes = jpaRepository.contarReservasVigentes(reserva.getViajeId(), reserva.getSillaId(), ahora);
        if (vigentes > 0) {
            throw SillaNoDisponibleException.paraSilla(reserva.getViajeId(), reserva.getSillaId());
        }

        try {
            ReservaJpaEntity guardada = jpaRepository.saveAndFlush(toEntity(reserva));
            return toDomain(guardada);
        } catch (DataIntegrityViolationException ex) {
            throw SillaNoDisponibleException.paraSilla(reserva.getViajeId(), reserva.getSillaId());
        }
    }

    @Override
    public Optional<Reserva> confirmarSiVigente(String reservaId, LocalDateTime ahora) {
        int modificadas = jpaRepository.confirmarReservaActivaVigente(reservaId, ahora);
        if (modificadas == 1) {
            return buscarPorId(reservaId);
        }
        return buscarPorId(reservaId)
                .filter(reserva -> reserva.getEstado() == EstadoReserva.CONFIRMADA);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Reserva> buscarPorId(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return jpaRepository.findById(id.trim())
                .map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Reserva> buscarReservaActiva(String viajeId, String sillaId, LocalDateTime ahora) {
        return jpaRepository.findFirstByViajeIdAndSillaIdAndEstadoAndFechaExpiracionAfter(
                viajeId,
                sillaId,
                EstadoReserva.ACTIVA.name(),
                ahora
        ).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existeReservaActiva(String viajeId, String sillaId, LocalDateTime ahora) {
        return jpaRepository.existsByViajeIdAndSillaIdAndEstadoAndFechaExpiracionAfter(
                viajeId,
                sillaId,
                EstadoReserva.ACTIVA.name(),
                ahora
        );
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existeReservaConfirmada(String viajeId, String sillaId) {
        return jpaRepository.existsByViajeIdAndSillaIdAndEstado(
                viajeId,
                sillaId,
                EstadoReserva.CONFIRMADA.name()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<Reserva> buscarReservasExpiradas(LocalDateTime ahora) {
        return jpaRepository.findByEstadoAndFechaExpiracionLessThanEqual(
                EstadoReserva.ACTIVA.name(),
                ahora
        ).stream()
         .map(this::toDomain)
         .toList();
    }

    // =========================================================================
    // MAPEO BIDIRECCIONAL ENTRE DOMINIO Y ENTIDAD JPA
    // =========================================================================

    private ReservaJpaEntity toEntity(Reserva reserva) {
        String id = reserva.getId();
        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString();
        }

        boolean ocupaSilla = reserva.getEstado() == EstadoReserva.ACTIVA || reserva.getEstado() == EstadoReserva.CONFIRMADA;

        return new ReservaJpaEntity(
                id,
                reserva.getViajeId(),
                reserva.getSillaId(),
                reserva.getPasajeroId(),
                reserva.getEstado().name(),
                ocupaSilla,
                reserva.getFechaCreacion(),
                reserva.getFechaExpiracion(),
                reserva.getFechaConfirmacion()
        );
    }

    private Reserva toDomain(ReservaJpaEntity entity) {
        EstadoReserva estado = EstadoReserva.valueOf(entity.getEstado());
        return new Reserva(
                entity.getId(),
                entity.getViajeId(),
                entity.getSillaId(),
                entity.getPasajeroId(),
                estado,
                entity.getFechaCreacion(),
                entity.getFechaExpiracion(),
                entity.getFechaConfirmacion()
        );
    }
}
