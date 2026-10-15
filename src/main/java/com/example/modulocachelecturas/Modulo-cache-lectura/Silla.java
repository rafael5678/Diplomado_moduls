package com.example.modulocachelecturas;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;

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
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "viaje_id", nullable = false)
    @JsonIgnore
    private Viaje viaje;

    @Column(nullable = false)
    private Integer numeroSilla;

    @Column(nullable = false)
    private Integer piso; // 1 o 2

    @Column(nullable = false, length = 20)
    private String ubicacion; // "VENTANA", "PASILLO"

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoSilla estado; // DISPONIBLE, RESERVADA, OCUPADA

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal precio;
}
