package com.example.modulocachelecturas.reservas.repository;

import com.example.modulocachelecturas.reservas.model.EstadoReserva;
import com.example.modulocachelecturas.reservas.model.ReservaSilla;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * REPOSITORIO JPA PARA LA TABLA 'reservas_silla' (ADR-004).
 *
 * Los cambios de estado se hacen con UPDATE condicionales ("... WHERE estado = APARTADA"):
 * si dos procesos intentan cambiar la misma reserva a la vez (por ejemplo, el pago llega
 * justo cuando el liberador la está venciendo), solo uno modifica la fila y el otro recibe 0.
 */
@Repository
public interface ReservaSillaRepository extends JpaRepository<ReservaSilla, Long> {

    /**
     * Pasa una reserva de APARTADA a VENDIDA, solo si todavía no ha vencido.
     * Retorna 1 si se vendió, 0 si ya no estaba APARTADA o si ya se venció.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE ReservaSilla r SET r.estado = :vendida, r.actualizadaEn = :ahora " +
           "WHERE r.id = :id AND r.estado = :apartada AND r.venceEn > :ahora")
    int marcarVendidaSiVigente(@Param("id") Long id,
                               @Param("ahora") LocalDateTime ahora,
                               @Param("apartada") EstadoReserva apartada,
                               @Param("vendida") EstadoReserva vendida);

    /**
     * Pasa una reserva de APARTADA a LIBERADA y borra su llave activa para que la silla
     * se pueda volver a apartar. Retorna 1 si se liberó, 0 si ya no estaba APARTADA.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE ReservaSilla r SET r.estado = :liberada, r.llaveActiva = NULL, r.actualizadaEn = :ahora " +
           "WHERE r.id = :id AND r.estado = :apartada")
    int marcarLiberadaSiApartada(@Param("id") Long id,
                                 @Param("ahora") LocalDateTime ahora,
                                 @Param("apartada") EstadoReserva apartada,
                                 @Param("liberada") EstadoReserva liberada);

    /**
     * Reservas APARTADAS cuyo tiempo ya venció, de la más antigua a la más nueva.
     * Se consulta por lotes (Pageable) para no cargar miles de filas en el pico.
     */
    @Query("SELECT r FROM ReservaSilla r WHERE r.estado = :apartada AND r.venceEn <= :ahora ORDER BY r.venceEn ASC")
    List<ReservaSilla> buscarVencidas(@Param("apartada") EstadoReserva apartada,
                                      @Param("ahora") LocalDateTime ahora,
                                      Pageable lote);

    /**
     * Cuenta reservas en un estado que vencieron antes de un límite.
     * Se usa para alertar si el proceso de liberación se detuvo.
     */
    long countByEstadoAndVenceEnBefore(EstadoReserva estado, LocalDateTime limite);
}
