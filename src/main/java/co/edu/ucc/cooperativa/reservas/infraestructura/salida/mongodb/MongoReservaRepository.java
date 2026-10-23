package co.edu.ucc.cooperativa.reservas.infraestructura.salida.mongodb;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repositorio de Spring Data MongoDB para la colección de reservas.
 */
@Repository("cooperativaMongoReservaRepository")
public interface MongoReservaRepository extends MongoRepository<ReservaDocument, String> {

    boolean existsByViajeIdAndSillaIdAndEstadoAndFechaExpiracionAfter(
            String viajeId,
            String sillaId,
            String estado,
            LocalDateTime ahora
    );

    boolean existsByViajeIdAndSillaIdAndEstado(String viajeId, String sillaId, String estado);

    Optional<ReservaDocument> findFirstByViajeIdAndSillaIdAndEstadoAndFechaExpiracionAfter(
            String viajeId,
            String sillaId,
            String estado,
            LocalDateTime ahora
    );

    List<ReservaDocument> findByEstadoAndFechaExpiracionLessThanEqual(
            String estado,
            LocalDateTime ahora
    );
}
