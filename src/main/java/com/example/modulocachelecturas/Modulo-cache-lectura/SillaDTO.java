package com.example.modulocachelecturas;

import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SillaDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Integer numeroSilla;
    private Integer piso;
    private String ubicacion;
    private EstadoSilla estado;
    private BigDecimal precio;
}
