package co.edu.ucc.cooperativa.reservas.adaptadores.entrada.rest;

/**
 * DTO de solicitud HTTP para apartar una silla (ADR-003).
 */
public record ReservaRequestDto(
        String viajeId,
        String sillaId,
        String pasajeroId
) {
    public ReservaRequestDto {
        if (viajeId != null) viajeId = viajeId.trim();
        if (sillaId != null) sillaId = sillaId.trim();
        if (pasajeroId != null) pasajeroId = pasajeroId.trim();
    }
}
