package co.edu.ucc.cooperativa.reservas.dominio;

import java.util.Objects;

/**
 * Entidad de dominio que representa una Silla dentro de un viaje de la cooperativa.
 * Pure Java 17+: Sin Spring, sin Lombok, sin dependencias de persistencia.
 */
public class Silla {

    private final String sillaId;
    private final String viajeId;
    private boolean disponible;

    public Silla(String sillaId, String viajeId, boolean disponible) {
        if (sillaId == null || sillaId.isBlank()) {
            throw new IllegalArgumentException("El identificador de silla (sillaId) es obligatorio");
        }
        if (viajeId == null || viajeId.isBlank()) {
            throw new IllegalArgumentException("El identificador de viaje (viajeId) es obligatorio");
        }

        this.sillaId = sillaId.trim();
        this.viajeId = viajeId.trim();
        this.disponible = disponible;
    }

    public static Silla crearDisponible(String sillaId, String viajeId) {
        return new Silla(sillaId, viajeId, true);
    }

    public boolean estaDisponible() {
        return this.disponible;
    }

    public void apartar() {
        if (!estaDisponible()) {
            throw SillaNoDisponibleException.paraSilla(viajeId, sillaId);
        }
        this.disponible = false;
    }

    public void liberar() {
        this.disponible = true;
    }

    public String getSillaId() {
        return sillaId;
    }

    public String getViajeId() {
        return viajeId;
    }

    public boolean isDisponible() {
        return disponible;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Silla silla)) return false;
        return Objects.equals(sillaId, silla.sillaId) && Objects.equals(viajeId, silla.viajeId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sillaId, viajeId);
    }

    @Override
    public String toString() {
        return "Silla{" +
                "sillaId='" + sillaId + '\'' +
                ", viajeId='" + viajeId + '\'' +
                ", disponible=" + disponible +
                '}';
    }
}
