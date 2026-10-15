package com.example.modulocachelecturas;

import lombok.*;

import java.io.Serializable;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvalidateCacheRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long viajeId;
    private String motivo; // e.g., "RESERVA_SILLA", "CONFIRMACION_PAGO", "LIBERACION_SILLA"
}
