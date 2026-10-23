package co.edu.ucc.cooperativa.reservas.dominio;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Pruebas Unitarias de Dominio: Entidad Silla (Cooperativa)")
class SillaTest {

    @Test
    @DisplayName("Debe crear una silla disponible con atributos válidos")
    void debeCrearSillaDisponible() {
        Silla silla = Silla.crearDisponible("S-12", "VJ-01");

        assertNotNull(silla);
        assertEquals("S-12", silla.getSillaId());
        assertEquals("VJ-01", silla.getViajeId());
        assertTrue(silla.estaDisponible());
        assertTrue(silla.isDisponible());
    }

    @Test
    @DisplayName("Debe apartar una silla disponible exitosamente")
    void debeApartarSillaDisponible() {
        Silla silla = Silla.crearDisponible("S-12", "VJ-01");

        silla.apartar();

        assertFalse(silla.estaDisponible());
        assertFalse(silla.isDisponible());
    }

    @Test
    @DisplayName("Debe lanzar SillaNoDisponibleException al intentar apartar una silla no disponible")
    void debeFallarAlApartarSillaNoDisponible() {
        Silla silla = new Silla("S-12", "VJ-01", false);

        SillaNoDisponibleException ex = assertThrows(
                SillaNoDisponibleException.class,
                silla::apartar
        );

        assertTrue(ex.getMessage().contains("S-12"));
        assertTrue(ex.getMessage().contains("VJ-01"));
    }

    @Test
    @DisplayName("Debe liberar una silla apartada")
    void debeLiberarSilla() {
        Silla silla = Silla.crearDisponible("S-12", "VJ-01");
        silla.apartar();
        assertFalse(silla.estaDisponible());

        silla.liberar();
        assertTrue(silla.estaDisponible());
    }

    @Test
    @DisplayName("Debe rechazar identificadores nulos o en blanco en el constructor")
    void debeRechazarDatosInvalidos() {
        assertThrows(IllegalArgumentException.class, () -> new Silla(null, "VJ-01", true));
        assertThrows(IllegalArgumentException.class, () -> new Silla("   ", "VJ-01", true));
        assertThrows(IllegalArgumentException.class, () -> new Silla("S-12", null, true));
        assertThrows(IllegalArgumentException.class, () -> new Silla("S-12", "   ", true));
    }
}
