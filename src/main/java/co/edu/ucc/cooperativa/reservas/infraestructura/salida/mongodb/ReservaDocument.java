package co.edu.ucc.cooperativa.reservas.infraestructura.salida.mongodb;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * Documento MongoDB para la persistencia del agregado Reserva (Colección "reservas").
 */
@Document(collection = "reservas")
@CompoundIndexes({
        @CompoundIndex(name = "idx_coop_viaje_silla_estado", def = "{'viajeId': 1, 'sillaId': 1, 'estado': 1}"),
        @CompoundIndex(name = "idx_coop_estado_expira", def = "{'estado': 1, 'fechaExpiracion': 1}"),
        @CompoundIndex(
                name = "ux_coop_viaje_silla_ocupada",
                def = "{'viajeId': 1, 'sillaId': 1}",
                unique = true,
                partialFilter = "{'$or': [{'ocupaSilla': true}, {'estado': {'$in': ['ACTIVA', 'CONFIRMADA']}}]}"
        )
})
public class ReservaDocument {

    @Id
    private String id;

    @Indexed
    private String viajeId;

    @Indexed
    private String sillaId;

    private String pasajeroId;
    private String estado;
    private boolean ocupaSilla;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaExpiracion;
    private LocalDateTime fechaConfirmacion;

    public ReservaDocument() {
    }

    public ReservaDocument(
            String id,
            String viajeId,
            String sillaId,
            String pasajeroId,
            String estado,
            LocalDateTime fechaCreacion,
            LocalDateTime fechaExpiracion,
            LocalDateTime fechaConfirmacion
    ) {
        this.id = id;
        this.viajeId = viajeId;
        this.sillaId = sillaId;
        this.pasajeroId = pasajeroId;
        this.estado = estado;
        this.ocupaSilla = "ACTIVA".equals(estado) || "CONFIRMADA".equals(estado);
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
        this.ocupaSilla = "ACTIVA".equals(estado) || "CONFIRMADA".equals(estado);
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
