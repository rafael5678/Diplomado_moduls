package co.edu.ucc.cooperativa.reservas.infraestructura.salida.mysql;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repositorio Spring Data JPA para la entidad de Reservas en MySQL.
 */
@Repository("cooperativaMySqlReservaRepository")
public interface MySqlReservaRepository extends JpaRepository<ReservaJpaEntity, String> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE ReservaJpaEntity r SET r.estado = 'EXPIRADA', r.ocupaSilla = false " +
           "WHERE r.viajeId = :viajeId AND r.sillaId = :sillaId AND r.estado = 'ACTIVA' AND r.fechaExpiracion <= :ahora")
    int liberarReservasActivasVencidas(
            @Param("viajeId") String viajeId,
            @Param("sillaId") String sillaId,
            @Param("ahora") LocalDateTime ahora
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE ReservaJpaEntity r SET r.estado = 'CONFIRMADA', r.fechaConfirmacion = :ahora, r.ocupaSilla = true " +
           "WHERE r.id = :reservaId AND r.estado = 'ACTIVA' AND r.fechaExpiracion > :ahora")
    int confirmarReservaActivaVigente(
            @Param("reservaId") String reservaId,
            @Param("ahora") LocalDateTime ahora
    );

    boolean existsByViajeIdAndSillaIdAndEstadoAndFechaExpiracionAfter(
            String viajeId,
            String sillaId,
            String estado,
            LocalDateTime ahora
    );

    boolean existsByViajeIdAndSillaIdAndEstado(String viajeId, String sillaId, String estado);

    Optional<ReservaJpaEntity> findFirstByViajeIdAndSillaIdAndEstadoAndFechaExpiracionAfter(
            String viajeId,
            String sillaId,
            String estado,
            LocalDateTime ahora
    );

    List<ReservaJpaEntity> findByEstadoAndFechaExpiracionLessThanEqual(
            String estado,
            LocalDateTime ahora
    );

    @Query("SELECT COUNT(r) FROM ReservaJpaEntity r WHERE r.viajeId = :viajeId AND r.sillaId = :sillaId " +
           "AND (r.ocupaSilla = true OR (r.estado = 'ACTIVA' AND r.fechaExpiracion > :ahora))")
    long contarReservasVigentes(@Param("viajeId") String viajeId, @Param("sillaId") String sillaId, @Param("ahora") LocalDateTime ahora);
}
