package co.edu.ucc.cooperativa.reservas.infraestructura.salida.mysql;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Entidad JPA para la persistencia del agregado Reserva en MySQL (Tabla "reservas").
 *
 * Mapeo relacional con índice compuesto y restricción de concurrencia.
 */
@Entity
@Table(
        name = "reservas",
        indexes = {
                @Index(name = "idx_reserva_viaje_silla_estado", columnList = "viaje_id, silla_id, estado"),
                @Index(name = "idx_reserva_estado_expiracion", columnList = "estado, fecha_expiracion")
        }
)
public class ReservaJpaEntity {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @Column(name = "viaje_id", length = 64, nullable = false)
    private String viajeId;

    @Column(name = "silla_id", length = 32, nullable = false)
    private String sillaId;

    @Column(name = "pasajero_id", length = 64, nullable = false)
    private String pasajeroId;

    @Column(name = "estado", length = 20, nullable = false)
    private String estado;

    @Column(name = "ocupa_silla", nullable = false)
    private boolean ocupaSilla;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion;

    @Column(name = "fecha_expiracion", nullable = false)
    private LocalDateTime fechaExpiracion;

    @Column(name = "fecha_confirmacion")
    private LocalDateTime fechaConfirmacion;

    public ReservaJpaEntity() {
    }

    public ReservaJpaEntity(
            String id,
            String viajeId,
            String sillaId,
            String pasajeroId,
            String estado,
            boolean ocupaSilla,
            LocalDateTime fechaCreacion,
            LocalDateTime fechaExpiracion,
            LocalDateTime fechaConfirmacion
    ) {
        this.id = id;
        this.viajeId = viajeId;
        this.sillaId = sillaId;
        this.pasajeroId = pasajeroId;
        this.estado = estado;
        this.ocupaSilla = ocupaSilla;
        this.fechaCreacion = fechaCreacion;
        this.fechaExpiracion = fechaExpiracion;
        this.fechaConfirmacion = fechaConfirmacion;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getViajeId() {
        return viajeId;
    }

    public void setViajeId(String viajeId) {
        this.viajeId = viajeId;
    }

    public String getSillaId() {
        return sillaId;
    }

    public void setSillaId(String sillaId) {
        this.sillaId = sillaId;
    }

    public String getPasajeroId() {
        return pasajeroId;
    }

    public void setPasajeroId(String pasajeroId) {
        this.pasajeroId = pasajeroId;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public boolean isOcupaSilla() {
        return ocupaSilla;
    }

    public void setOcupaSilla(boolean ocupaSilla) {
        this.ocupaSilla = ocupaSilla;
    }

    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    public void setFechaCreacion(LocalDateTime fechaCreacion) {
        this.fechaCreacion = fechaCreacion;
    }

    public LocalDateTime getFechaExpiracion() {
        return fechaExpiracion;
    }

    public void setFechaExpiracion(LocalDateTime fechaExpiracion) {
        this.fechaExpiracion = fechaExpiracion;
    }

    public LocalDateTime getFechaConfirmacion() {
        return fechaConfirmacion;
    }

    public void setFechaConfirmacion(LocalDateTime fechaConfirmacion) {
        this.fechaConfirmacion = fechaConfirmacion;
    }
}
