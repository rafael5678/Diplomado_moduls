package com.example.modulocachelecturas.model;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "viajes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Viaje implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String codigoViaje;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "ruta_id", nullable = false)
    private Ruta ruta;

    @Column(nullable = false)
    private LocalDate fechaSalida;

    @Column(nullable = false)
    private LocalTime horaSalida;

    @Column(nullable = false, length = 20)
    private String busPlaca;

    @Column(nullable = false)
    private Integer capacidadTotal;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal precioBase;

    @Column(nullable = false, length = 30)
    private String estado;

    @OneToMany(mappedBy = "viaje", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Silla> sillas = new ArrayList<>();
}
