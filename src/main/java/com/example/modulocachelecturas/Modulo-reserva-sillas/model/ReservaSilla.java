package com.example.modulocachelecturas.reservas.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * ENTIDAD JPA: Reserva de una silla (ADR-004).
 *
 * Regla que garantiza el ASR 2 (0 sillas vendidas dos veces):
 * la columna 'llave_activa' guarda "viajeId:numeroSilla" mientras la reserva está
 * APARTADA o VENDIDA, y tiene restricción UNIQUE. Si dos servidores intentan apartar
 * la misma silla en el mismo segundo, la base de datos deja pasar solo una inserción.
 * Al liberar la reserva, 'llave_activa' queda en NULL para que la silla se pueda volver a apartar
 * (PostgreSQL permite varios NULL en una columna UNIQUE).
 */
@Entity
@Table(
        name = "reservas_silla",
        uniqueConstraints = @UniqueConstraint(name = "uq_reserva_silla_activa", columnNames = "llave_activa"),
        indexes = @Index(name = "idx_reserva_estado_vence", columnList = "estado, vence_en")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReservaSilla {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "viaje_id", nullable = false)
    private Long viajeId; // Viaje al que pertenece la silla

    @Column(name = "numero_silla", nullable = false)
    private Integer numeroSilla; // Número visible de la silla (1 al 40)

    @Column(name = "pasajero_id", nullable = false, length = 60)
    private String pasajeroId; // Documento o id del pasajero que apartó la silla

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoReserva estado;

    @Column(name = "llave_activa", length = 40)
    private String llaveActiva; // "viajeId:numeroSilla" mientras está activa; NULL si se liberó

    @Column(name = "creada_en", nullable = false)
    private LocalDateTime creadaEn;

    @Column(name = "vence_en", nullable = false)
    private LocalDateTime venceEn; // creadaEn + 10 minutos

    @Column(name = "actualizada_en")
    private LocalDateTime actualizadaEn;

    /**
     * Arma la llave única de una silla dentro de un viaje: "viajeId:numeroSilla".
     */
    public static String llaveDe(Long viajeId, Integer numeroSilla) {
        return viajeId + ":" + numeroSilla;
    }
}
