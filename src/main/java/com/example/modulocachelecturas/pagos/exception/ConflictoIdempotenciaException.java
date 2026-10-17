package com.example.modulocachelecturas.pagos.exception;

public class ConflictoIdempotenciaException
        extends RuntimeException {

    public ConflictoIdempotenciaException(String mensaje) {
        super(mensaje);
    }
}
