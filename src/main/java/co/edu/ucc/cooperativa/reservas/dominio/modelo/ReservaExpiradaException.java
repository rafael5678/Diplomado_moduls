package co.edu.ucc.cooperativa.reservas.dominio.modelo;

import java.time.LocalDateTime;

/**
 * Excepción lanzada cuando una operación intenta realizarse sobre una reserva
 * cuyo tiempo límite de 10 minutos ya ha expirado.
 */
public class ReservaExpiradaException extends DominioException {

    public ReservaExpiradaException(String mensaje) {
        super(mensaje);
    }

    public static ReservaExpiradaException porTiempo(String reservaId, LocalDateTime fechaExpiracion) {
        return new ReservaExpiradaException(
                String.format("La reserva '%s' ha expirado el '%s'. Se ha superado el tiempo límite de 10 minutos (ADR-003).",
                        reservaId, fechaExpiracion)
        );
    }
}
