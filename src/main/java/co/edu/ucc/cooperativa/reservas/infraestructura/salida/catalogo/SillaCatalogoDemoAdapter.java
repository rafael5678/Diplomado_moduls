package co.edu.ucc.cooperativa.reservas.infraestructura.salida.catalogo;

import co.edu.ucc.cooperativa.reservas.aplicacion.puertos.salida.SillaCatalogoPort;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Catálogo en memoria para ejecutar y demostrar el módulo sin una fuente de viajes/sillas.
 * Reemplazar por un adaptador conectado al inventario cuando este esté disponible.
 */
@Component("cooperativaSillaCatalogoDemoAdapter")
public class SillaCatalogoDemoAdapter implements SillaCatalogoPort {

    private final Map<String, Set<String>> sillasPorViaje = Map.of(
            "VJ-COOP-01", crearSillas(40),
            "VJ-COOP-02", crearSillas(32)
    );

    @Override
    public boolean existeEnViaje(String viajeId, String sillaId) {
        if (viajeId == null || sillaId == null) {
            return false;
        }

        return sillasPorViaje.getOrDefault(viajeId.trim(), Set.of()).contains(sillaId.trim());
    }

    private static Set<String> crearSillas(int cantidad) {
        return IntStream.rangeClosed(1, cantidad)
                .mapToObj(numero -> "S-" + numero)
                .collect(Collectors.toUnmodifiableSet());
    }
}
