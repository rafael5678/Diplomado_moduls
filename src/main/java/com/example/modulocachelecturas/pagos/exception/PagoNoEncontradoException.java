package com.example.modulocachelecturas.pagos.exception;

public class PagoNoEncontradoException extends RuntimeException {

    public PagoNoEncontradoException(String mensaje) {
        super(mensaje);
    }
}
