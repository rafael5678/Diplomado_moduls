package com.example.modulocachelecturas.reservas.service;

/**
 * ERRORES DE NEGOCIO DEL MÓDULO DE RESERVAS (ADR-004).
 *
 * Se agrupan en un solo archivo, igual que los DTOs del módulo de caché.
 * El controlador los traduce a códigos HTTP (404 y 409).
 */
public final class ReservaExceptions {

    private ReservaExceptions() {
    }

    /**
     * El viaje, la silla o la reserva no existen (HTTP 404).
     */
    public static class RecursoNoEncontradoException extends RuntimeException {
        public RecursoNoEncontradoException(String mensaje) {
            super(mensaje);
        }
    }

    /**
     * La silla ya está apartada o vendida a otro pasajero (HTTP 409).
     * Es la respuesta que recibe el segundo pasajero cuando dos eligen la misma silla.
     */
    public static class SillaNoDisponibleException extends RuntimeException {
        public SillaNoDisponibleException(String mensaje) {
            super(mensaje);
        }
    }

    /**
     * La reserva no se puede confirmar o cancelar en su estado actual (HTTP 409).
     * Ejemplo: el pago llegó después de los 10 minutos y la silla ya se liberó;
     * el módulo de Pagos debe devolver el dinero (ADR-005).
     */
    public static class ReservaNoModificableException extends RuntimeException {
        public ReservaNoModificableException(String mensaje) {
            super(mensaje);
        }
    }
}
