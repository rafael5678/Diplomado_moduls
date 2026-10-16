package com.example.modulocachelecturas.pagos;

public class ConflictoIdempotenciaException
        extends RuntimeException {

    public ConflictoIdempotenciaException(String mensaje) {
        super(mensaje);
    }
}
