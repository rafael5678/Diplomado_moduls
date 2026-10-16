package com.example.modulocachelecturas.model;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;

/**
 * ENTIDAD JPA: Ruta de Transporte.
 * Representa el trayecto entre una ciudad de origen y una ciudad de destino.
 */
@Entity
@Table(name = "rutas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Ruta implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // Identificador numérico autoincremental de la ruta

    @Column(nullable = false, unique = true, length = 50)
    private String codigoRuta; // Código de negocio (ej: "RUTA-BOG-MED")

    @Column(nullable = false, length = 100)
    private String origen; // Ciudad o terminal de origen

    @Column(nullable = false, length = 100)
    private String destino; // Ciudad o terminal de destino

    @Column(nullable = false)
    private Double distanciaKm; // Distancia aproximada en kilómetros

    @Column(nullable = false)
    private Double duracionEstimadaHoras; // Tiempo estimado de viaje en horas
}
