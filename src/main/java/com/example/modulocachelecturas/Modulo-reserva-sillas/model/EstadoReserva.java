package com.example.modulocachelecturas.reservas.model;

/**
 * ESTADOS DE UNA RESERVA DE SILLA (ADR-004).
 *
 * - APARTADA : la silla queda a nombre del pasajero por 10 minutos mientras paga.
 * - VENDIDA  : el módulo de Pagos confirmó el cobro; la silla es del pasajero.
 * - LIBERADA : el pasajero canceló o se venció el tiempo; la silla vuelve a estar libre.
 */
public enum EstadoReserva {
    APARTADA,
    VENDIDA,
    LIBERADA
}
