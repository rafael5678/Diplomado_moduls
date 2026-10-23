package co.edu.ucc.cooperativa.reservas.infraestructura.salida.catalogo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Catálogo de demostración de sillas")
class SillaCatalogoDemoAdapterTest {

    private final SillaCatalogoDemoAdapter catalogo = new SillaCatalogoDemoAdapter();

    @Test
    @DisplayName("Acepta una silla incluida en el viaje")
    void aceptaSillaExistenteEnViaje() {
        assertTrue(catalogo.existeEnViaje("VJ-COOP-01", "S-15"));
    }

    @Test
    @DisplayName("Rechaza una silla que no está en el catálogo")
    void rechazaSillaInexistente() {
        assertFalse(catalogo.existeEnViaje("VJ-COOP-01", "S-41"));
    }

    @Test
    @DisplayName("Rechaza una silla que pertenece a otro viaje")
    void rechazaSillaDeOtroViaje() {
        assertTrue(catalogo.existeEnViaje("VJ-COOP-01", "S-35"));
        assertFalse(catalogo.existeEnViaje("VJ-COOP-02", "S-35"));
    }

    @Test
    @DisplayName("Rechaza identificadores nulos y viajes desconocidos")
    void rechazaIdentificadoresDesconocidos() {
        assertFalse(catalogo.existeEnViaje(null, "S-15"));
        assertFalse(catalogo.existeEnViaje("VJ-DESCONOCIDO", "S-15"));
        assertFalse(catalogo.existeEnViaje("VJ-COOP-01", null));
    }
}
