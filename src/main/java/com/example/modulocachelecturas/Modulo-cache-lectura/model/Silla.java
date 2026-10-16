package com.example.modulocachelecturas.model;

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
    private Integer piso;

    @Column(nullable = false, length = 20)
    private String ubicacion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoSilla estado;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal precio;
}
