package com.example.modulocachelecturas.pagos;

public class PagoNoEncontradoException extends RuntimeException {

    public PagoNoEncontradoException(String mensaje) {
        super(mensaje);
    }
}
