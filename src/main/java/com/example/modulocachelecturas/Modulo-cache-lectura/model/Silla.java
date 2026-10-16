package com.example.modulocachelecturas.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * ENTIDAD JPA: Silla o Asiento Individual de Autobús.
 * Representa cada silla física dentro del bus para un viaje específico.
 */
@Entity
@Table(name = "sillas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Silla implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // Identificador único de la silla en la base de datos

    // Relación ManyToOne con Viaje: Varias sillas pertenecen a un mismo viaje
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "viaje_id", nullable = false)
    @JsonIgnore // Evita ciclos recursivos infinitos al serializar a JSON
    private Viaje viaje;

    @Column(nullable = false)
    private Integer numeroSilla; // Número visible del asiento (1 al 40)

    @Column(nullable = false)
    private Integer piso; // Nivel del autobús (Piso 1 o Piso 2)

    @Column(nullable = false, length = 20)
    private String ubicacion; // Distribución física: "VENTANA" o "PASILLO"

    // Mapeo del Enum como String en PostgreSQL ("DISPONIBLE", "RESERVADA", "OCUPADA")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoSilla estado;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal precio; // Precio particular del asiento según piso o comodidades
}
