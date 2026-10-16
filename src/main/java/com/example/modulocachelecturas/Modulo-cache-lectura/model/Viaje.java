package com.example.modulocachelecturas.model;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * ENTIDAD JPA: Viaje Programado.
 * Es la entidad agregada central del sistema de transporte. Asocia una ruta,
 * un vehículo (bus), fecha/hora de salida y la colección de sus 40 asientos.
 */
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
    private Long id; // Identificador

    @Column(nullable = false, unique = true, length = 50)
    private String codigoViaje; // Código despacho

    // origen/destino
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
    private Integer capacidadTotal; // Capacidad total de pasajeros (ej: 40 sillas)

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal precioBase; // Tarifa base del pasaje

    @Column(nullable = false, length = 30)
    private String estado; // Estado operativo: 'PROGRAMADO', 'EN_CURSO', 'FINALIZADO'

    // Relación OneToMany: Un viaje contiene múltiples sillas (asientos individuales)
    @OneToMany(mappedBy = "viaje", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Silla> sillas = new ArrayList<>();
}
