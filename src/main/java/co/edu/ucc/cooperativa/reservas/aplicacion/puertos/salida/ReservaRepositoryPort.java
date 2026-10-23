package co.edu.ucc.cooperativa.reservas.aplicacion.puertos.salida;

import co.edu.ucc.cooperativa.reservas.dominio.Reserva;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Puerto de Salida (Driven Port) para la persistencia del agregado Reserva.
 * Abstrae el motor de base de datos (PostgreSQL, MongoDB, memoria, etc.).
 */
public interface ReservaRepositoryPort {

    /**
     * Guarda o actualiza la entidad Reserva.
     */
    Reserva guardar(Reserva reserva);

    /** Adquiere atómicamente la ocupación para viaje/silla y persiste la reserva. */
    Reserva adquirirReserva(Reserva reserva, LocalDateTime ahora);

    /** Confirma únicamente si la reserva sigue activa y vigente en persistencia. */
    Optional<Reserva> confirmarSiVigente(String reservaId, LocalDateTime ahora);

    /**
     * Busca una reserva por su ID.
     */
    Optional<Reserva> buscarPorId(String id);

    /**
     * Busca si existe una reserva activa para una silla en un viaje a un instante de tiempo.
     */
    Optional<Reserva> buscarReservaActiva(String viajeId, String sillaId, LocalDateTime ahora);

    /**
     * Valida de forma rápida si una silla ya cuenta con reserva activa y vigente (ADR-003).
     */
    boolean existeReservaActiva(String viajeId, String sillaId, LocalDateTime ahora);

    /** Una reserva confirmada mantiene ocupada la silla indefinidamente en su viaje. */
    boolean existeReservaConfirmada(String viajeId, String sillaId);

    /**
     * Consulta reservas en estado ACTIVA que hayan superado su fecha de expiración.
     */
    List<Reserva> buscarReservasExpiradas(LocalDateTime ahora);
}
