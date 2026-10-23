package co.edu.ucc.cooperativa.reservas.dominio;

/**
 * Excepción base para reglas de negocio del dominio de reservas.
 * Pure Java 17+ sin dependencias externas.
 */
public abstract class DominioException extends RuntimeException {

    protected DominioException(String mensaje) {
        super(mensaje);
    }

    protected DominioException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
