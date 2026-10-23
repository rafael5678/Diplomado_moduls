package co.edu.ucc.cooperativa.reservas.infraestructura.salida.mongodb;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repositorio de Spring Data MongoDB para la colección de reservas.
 */
@Repository("cooperativaMongoReservaRepository")
public interface MongoReservaRepository extends MongoRepository<ReservaDocument, String> {

    @Query("{'viajeId': ?0, 'sillaId': ?1, 'estado': 'ACTIVA', 'fechaExpiracion': {'$lte': ?2}}")
    @Update("{'$set': {'estado': 'EXPIRADA', 'ocupaSilla': false}}")
    long liberarReservasActivasVencidas(String viajeId, String sillaId, LocalDateTime ahora);

    @Query("{'_id': ?0, 'estado': 'ACTIVA', 'fechaExpiracion': {'$gt': ?1}}")
    @Update("{'$set': {'estado': 'CONFIRMADA', 'fechaConfirmacion': ?1, 'ocupaSilla': true}}")
    long confirmarReservaActivaVigente(String reservaId, LocalDateTime ahora);

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

    long countByViajeIdAndSillaIdAndOcupaSillaTrue(String viajeId, String sillaId);
}
