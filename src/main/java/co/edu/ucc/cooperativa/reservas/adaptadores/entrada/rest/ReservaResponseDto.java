package co.edu.ucc.cooperativa.reservas.adaptadores.entrada.rest;

import co.edu.ucc.cooperativa.reservas.dominio.modelo.Reserva;

import java.time.LocalDateTime;

/**
 * DTO de respuesta HTTP desacoplado de las entidades de dominio.
 */
public record ReservaResponseDto(
        String id,
        String viajeId,
        String sillaId,
        String pasajeroId,
        String estado,
        LocalDateTime fechaCreacion,
        LocalDateTime fechaExpiracion,
        LocalDateTime fechaConfirmacion
) {
    public static ReservaResponseDto fromDomain(Reserva reserva) {
        if (reserva == null) {
            return null;
        }
        return new ReservaResponseDto(
                reserva.getId(),
                reserva.getViajeId(),
                reserva.getSillaId(),
                reserva.getPasajeroId(),
                reserva.getEstado().name(),
                reserva.getFechaCreacion(),
                reserva.getFechaExpiracion(),
                reserva.getFechaConfirmacion()
        );
    }
}
