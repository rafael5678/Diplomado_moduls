package co.edu.ucc.cooperativa.reservas.dominio;

/**
 * Excepción lanzada cuando una silla ya cuenta con una reserva ACTIVA
 * o no está disponible en el viaje solicitado (prevención de doble venta ADR-003).
 */
public class SillaNoDisponibleException extends DominioException {

    public SillaNoDisponibleException(String mensaje) {
        super(mensaje);
    }

    public static SillaNoDisponibleException paraSilla(String viajeId, String sillaId) {
        return new SillaNoDisponibleException(
                String.format("La silla '%s' ya se encuentra apartada con estado ACTIVO para el viaje '%s'", sillaId, viajeId)
        );
    }
}
