package co.edu.ucc.cooperativa.reservas.aplicacion.puertos.salida;

/** Consulta si una silla forma parte del inventario del viaje indicado. */
public interface SillaCatalogoPort {

    boolean existeEnViaje(String viajeId, String sillaId);
}
