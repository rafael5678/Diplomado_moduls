package com.example.modulocachelecturas;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;

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
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String codigoRuta;

    @Column(nullable = false, length = 100)
    private String origen;

    @Column(nullable = false, length = 100)
    private String destino;

    @Column(nullable = false)
    private Double distanciaKm;

    @Column(nullable = false)
    private Double duracionEstimadaHoras;
}
