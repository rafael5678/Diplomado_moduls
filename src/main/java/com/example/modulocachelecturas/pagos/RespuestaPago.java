package com.example.modulocachelecturas.pagos;

public record RespuestaPago(
        String referencia,
        String estado
) {

    public static RespuestaPago desde(Pago pago) {
        return new RespuestaPago(
                pago.getReferencia(),
                pago.getEstado().name()
        );
    }
}
