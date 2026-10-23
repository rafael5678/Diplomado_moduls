package co.edu.ucc.cooperativa.reservas.dominio.modelo;

/**
 * Estados del ciclo de vida de una reserva según regla de negocio ADR-003:
 * - ACTIVA     : Silla apartada temporalmente por 10 minutos para evitar doble reserva.
 * - EXPIRADA   : Superó los 10 minutos sin concretar pago; la silla se libera.
 * - CONFIRMADA : Pago completado y reserva confirmada definitivamente.
 */
public enum EstadoReserva {
    ACTIVA,
    EXPIRADA,
    CONFIRMADA
}
