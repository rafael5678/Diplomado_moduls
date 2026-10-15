package com.example.modulocachelecturas;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MapaSillasDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long viajeId;
    private String codigoViaje;
    private String rutaCodigo;
    private String origen;
    private String destino;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate fechaSalida;

    @JsonFormat(pattern = "HH:mm:ss")
    private LocalTime horaSalida;

    private String busPlaca;
    private int totalSillas;
    private int sillasDisponibles;
    private int sillasReservadas;
    private int sillasOcupadas;
    private List<SillaDTO> sillas;

    private String source; // "CACHE_REDIS (Hit)" o "DATABASE_POSTGRES (Miss)"
    private Long responseTimeMs;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime cachedAt;
}
