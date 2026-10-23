package co.edu.ucc.cooperativa.reservas.adaptadores.salida.persistencia;

import co.edu.ucc.cooperativa.reservas.dominio.modelo.EstadoReserva;
import co.edu.ucc.cooperativa.reservas.dominio.modelo.Reserva;
import co.edu.ucc.cooperativa.reservas.dominio.puertos.ReservaRepositoryPort;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptador Secundario / De Salida (Driven Adapter) para MongoDB.
 *
 * Implementa el puerto ReservaRepositoryPort, traduciendo entre la entidad
 * de dominio Reserva y el documento de MongoDB ReservaDocument.
 */
@Component("cooperativaReservaMongoAdapter")
public class ReservaMongoAdapter implements ReservaRepositoryPort {

    private final MongoReservaRepository mongoRepository;

    public ReservaMongoAdapter(MongoReservaRepository mongoRepository) {
        this.mongoRepository = mongoRepository;
    }

    @Override
    public Reserva guardar(Reserva reserva) {
        ReservaDocument document = toDocument(reserva);
        ReservaDocument guardado = mongoRepository.save(document);
        return toDomain(guardado);
    }

    @Override
    public Optional<Reserva> buscarPorId(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return mongoRepository.findById(id.trim())
                .map(this::toDomain);
    }

    @Override
    public Optional<Reserva> buscarReservaActiva(String viajeId, String sillaId, LocalDateTime ahora) {
        return mongoRepository.findFirstByViajeIdAndSillaIdAndEstadoAndFechaExpiracionAfter(
                viajeId,
                sillaId,
                EstadoReserva.ACTIVA.name(),
                ahora
        ).map(this::toDomain);
    }

    @Override
    public boolean existeReservaActiva(String viajeId, String sillaId, LocalDateTime ahora) {
        return mongoRepository.existsByViajeIdAndSillaIdAndEstadoAndFechaExpiracionAfter(
                viajeId,
                sillaId,
                EstadoReserva.ACTIVA.name(),
                ahora
        );
    }

    @Override
    public List<Reserva> buscarReservasExpiradas(LocalDateTime ahora) {
        return mongoRepository.findByEstadoAndFechaExpiracionBefore(EstadoReserva.ACTIVA.name(), ahora)
                .stream()
                .map(this::toDomain)
                .toList();
    }

    // =========================================================================
    // MAPEO BIDIRECCIONAL ENTRE DOMINIO Y DOCUMENTO
    // =========================================================================

    private ReservaDocument toDocument(Reserva reserva) {
        String id = reserva.getId();
        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString();
        }

        return new ReservaDocument(
                id,
                reserva.getViajeId(),
                reserva.getSillaId(),
                reserva.getPasajeroId(),
                reserva.getEstado().name(),
                reserva.getFechaCreacion(),
                reserva.getFechaExpiracion(),
                reserva.getFechaConfirmacion()
        );
    }

    private Reserva toDomain(ReservaDocument doc) {
        return new Reserva(
                doc.getId(),
                doc.getViajeId(),
                doc.getSillaId(),
                doc.getPasajeroId(),
                EstadoReserva.valueOf(doc.getEstado()),
                doc.getFechaCreacion(),
                doc.getFechaExpiracion(),
                doc.getFechaConfirmacion()
        );
    }
}
