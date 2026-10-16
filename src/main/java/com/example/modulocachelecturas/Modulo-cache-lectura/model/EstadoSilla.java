package com.example.modulocachelecturas.model;

/**
 * ENUMERACIÓN DE ESTADOS DE SILLA.
 *
 * Modela el ciclo de vida de un asiento en el autobús:
 * - DISPONIBLE : Asiento libre que puede ser consultado y seleccionado por un pasajero.
 * - RESERVADA  : Asiento apartado temporalmente mientras el módulo de Pagos procesa la transacción.
 * - OCUPADA    : Asiento vendido y confirmado definitivamente.
 */
public enum EstadoSilla {
    DISPONIBLE,
    RESERVADA,
    OCUPADA
}
