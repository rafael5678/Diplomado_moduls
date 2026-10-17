
package com.example.modulocachelecturas.pagos.service;

import com.example.modulocachelecturas.pagos.model.EstadoPago;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.stereotype.Component;

@Component
public class PasarelaSimulada {

    private final ConcurrentMap<String, EstadoPago> cobros =
            new ConcurrentHashMap<>();

    public EstadoPago cobrar(String referencia, Long valorEnPesos) {

        return cobros.computeIfAbsent(
                referencia,
                clave -> EstadoPago.APROBADO
        );
    }

    public EstadoPago consultar(String referencia) {

        return cobros.getOrDefault(
                referencia,
                EstadoPago.EN_PROCESO
        );
    }
}
