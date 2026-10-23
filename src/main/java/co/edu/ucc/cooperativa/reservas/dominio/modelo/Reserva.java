package co.edu.ucc.cooperativa.reservas.dominio.modelo;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Agregado Raíz / Entidad Reserva en el dominio de Cooperativa de Transporte.
 * Implementa la regla del ADR-003: la silla se aparta temporalmente por 10 minutos
 * con estado ACTIVA para evitar doble reserva sobre el mismo viaje.
 *
 * Pure Java 17+: Sin Lombok, sin Spring, sin persistencia.
 */
public class Reserva {

    public static final int MINUTOS_VIGENCIA = 10;

    private final String id;
    private final String viajeId;
    private final String sillaId;
    private final String pasajeroId;
    private EstadoReserva estado;
    private final LocalDateTime fechaCreacion;
    private final LocalDateTime fechaExpiracion;
    private LocalDateTime fechaConfirmacion;

    public Reserva(
            String id,
            String viajeId,
            String sillaId,
            String pasajeroId,
            EstadoReserva estado,
            LocalDateTime fechaCreacion,
            LocalDateTime fechaExpiracion,
            LocalDateTime fechaConfirmacion
    ) {
        if (viajeId == null || viajeId.isBlank()) {
            throw new IllegalArgumentException("El viajeId es obligatorio");
        }
        if (sillaId == null || sillaId.isBlank()) {
            throw new IllegalArgumentException("El sillaId es obligatorio");
        }
        if (pasajeroId == null || pasajeroId.isBlank()) {
            throw new IllegalArgumentException("El pasajeroId es obligatorio");
        }
        if (fechaCreacion == null) {
            throw new IllegalArgumentException("La fechaCreacion es obligatoria");
        }
        if (fechaExpiracion == null) {
            throw new IllegalArgumentException("La fechaExpiracion es obligatoria");
        }
        if (!fechaExpiracion.isAfter(fechaCreacion)) {
            throw new IllegalArgumentException("La fecha de expiración debe ser estrictamente posterior a la fecha de creación (ADR-003: 10 minutos)");
        }

        this.id = (id == null || id.isBlank()) ? UUID.randomUUID().toString() : id.trim();
        this.viajeId = viajeId.trim();
        this.sillaId = sillaId.trim();
        this.pasajeroId = pasajeroId.trim();
        this.estado = (estado == null) ? EstadoReserva.ACTIVA : estado;
        this.fechaCreacion = fechaCreacion;
        this.fechaExpiracion = fechaExpiracion;
        this.fechaConfirmacion = fechaConfirmacion;
    }

    /**
     * Factory method para crear una nueva reserva apartada con vigencia de 10 minutos.
     */
    public static Reserva crear(String viajeId, String sillaId, String pasajeroId, LocalDateTime ahora) {
        if (ahora == null) {
            throw new IllegalArgumentException("La fecha y hora de referencia actual no pueden ser nulas");
        }
        LocalDateTime fechaExpiracion = ahora.plusMinutes(MINUTOS_VIGENCIA);
        return new Reserva(
                null,
                viajeId,
                sillaId,
                pasajeroId,
                EstadoReserva.ACTIVA,
                ahora,
                fechaExpiracion,
                null
        );
    }

    // =========================================================================
    // REGLAS Y COMPORTAMIENTO DE NEGOCIO (ADR-003)
    // =========================================================================

    /**
     * Verifica si la reserva ha superado los 10 minutos de vigencia.
     */
    public boolean estaExpirada(LocalDateTime ahora) {
        Objects.requireNonNull(ahora, "La fecha actual no puede ser nula");
        return ahora.isAfter(this.fechaExpiracion);
    }

    /**
     * Verifica si la reserva está activa y vigente.
     */
    public boolean estaActiva(LocalDateTime ahora) {
        return this.estado == EstadoReserva.ACTIVA && !estaExpirada(ahora);
    }

    /**
     * Confirma la reserva convirtiéndola en venta efectiva.
     * Si han pasado los 10 minutos, lanza ReservaExpiradaException para liberar la silla.
     */
    public void confirmar(LocalDateTime ahora) {
        Objects.requireNonNull(ahora, "La fecha de confirmación no puede ser nula");

        if (this.estado == EstadoReserva.CONFIRMADA) {
            return; // Idempotencia
        }

        if (estaExpirada(ahora)) {
            this.estado = EstadoReserva.EXPIRADA;
            throw ReservaExpiradaException.porTiempo(this.id, this.fechaExpiracion);
        }

        if (this.estado != EstadoReserva.ACTIVA) {
            throw new IllegalStateException(
                    String.format("No se puede confirmar la reserva '%s' en estado: %s", this.id, this.estado)
            );
        }

        this.estado = EstadoReserva.CONFIRMADA;
        this.fechaConfirmacion = ahora;
    }

    /**
     * Marca la reserva como EXPIRADA si superó la ventana de 10 minutos.
     */
    public void expirar(LocalDateTime ahora) {
        if (this.estado == EstadoReserva.ACTIVA && estaExpirada(ahora)) {
            this.estado = EstadoReserva.EXPIRADA;
        }
    }

    /**
     * Llave compuesta de bloqueo para garantizar el ASR de 0 sillas vendidas dos veces.
     */
    public String llaveActiva() {
        return viajeId + ":" + sillaId;
    }

    // =========================================================================
    // GETTERS EXPLÍCITOS (SIN LOMBOK)
    // =========================================================================

    public String getId() {
        return id;
    }

    public String getViajeId() {
        return viajeId;
    }

    public String getSillaId() {
        return sillaId;
    }

    public String getPasajeroId() {
        return pasajeroId;
    }

    public EstadoReserva getEstado() {
        return estado;
    }

    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    public LocalDateTime getFechaExpiracion() {
        return fechaExpiracion;
    }

    public LocalDateTime getFechaConfirmacion() {
        return fechaConfirmacion;
    }

    // =========================================================================
    // IDENTIDAD Y EQUALS / HASHCODE
    // =========================================================================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Reserva reserva)) return false;
        return Objects.equals(id, reserva.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Reserva{" +
                "id='" + id + '\'' +
                ", viajeId='" + viajeId + '\'' +
                ", sillaId='" + sillaId + '\'' +
                ", pasajeroId='" + pasajeroId + '\'' +
                ", estado=" + estado +
                ", fechaCreacion=" + fechaCreacion +
                ", fechaExpiracion=" + fechaExpiracion +
                '}';
    }
}
