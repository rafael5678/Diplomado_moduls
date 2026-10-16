package com.example.modulocachelecturas.reservas.service;

/**
 * EVENTO INTERNO: una silla cambió de estado (apartada, vendida o liberada).
 *
 * El servicio de reservas lo publica dentro de la transacción. Un listener lo recibe
 * después del commit y le pide al módulo de caché que borre el mapa de ese viaje.
 * Así el módulo de reservas no depende de cómo funciona la caché por dentro.
 *
 * @param viajeId     viaje de la silla
 * @param numeroSilla número visible de la silla
 * @param motivo      SILLA_APARTADA, SILLA_VENDIDA, RESERVA_CANCELADA o RESERVA_VENCIDA
 */
public record SillaCambioEstadoEvent(Long viajeId, Integer numeroSilla, String motivo) {
}
