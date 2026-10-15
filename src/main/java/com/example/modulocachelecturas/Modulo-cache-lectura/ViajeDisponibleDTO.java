package com.example.modulocachelecturas;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ViajeDisponibleDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long viajeId;
    private String codigoViaje;
    private String origen;
    private String destino;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate fechaSalida;

    @JsonFormat(pattern = "HH:mm:ss")
    private LocalTime horaSalida;

    private String busPlaca;
    private BigDecimal precioBase;
    private int sillasDisponibles;
    private int capacidadTotal;
    private String estado;
    private String source;
}
