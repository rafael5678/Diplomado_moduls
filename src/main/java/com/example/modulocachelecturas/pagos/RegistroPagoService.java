
package com.example.modulocachelecturas.pagos;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistroPagoService {

    private final PagoRepository repositorio;

    public RegistroPagoService(PagoRepository repositorio) {
        this.repositorio = repositorio;
    }

    @Transactional
    public Pago registrar(
            String llave,
            String reservaId,
            String documento,
            Long valorEnPesos) {

        Pago pago = new Pago(
                llave,
                reservaId,
                documento,
                valorEnPesos,
                UUID.randomUUID().toString()
        );

        return repositorio.saveAndFlush(pago);
    }

    @Transactional
    public Pago actualizarEstado(Long pagoId, EstadoPago nuevoEstado) {

        Pago pago = repositorio.findById(pagoId)
                .orElseThrow(() ->
                        new IllegalStateException("No se encontró el pago")
                );

        if (pago.getEstado() != EstadoPago.EN_PROCESO) {

            if (pago.getEstado() == nuevoEstado) {
                return pago;
            }

            throw new IllegalStateException(
                    "No se puede modificar un pago finalizado"
            );
        }

        if (nuevoEstado == EstadoPago.EN_PROCESO) {
            throw new IllegalArgumentException(
                    "El nuevo estado debe ser APROBADO o RECHAZADO"
            );
        }

        pago.cambiarEstado(nuevoEstado);

        return repositorio.saveAndFlush(pago);
    }

    @Transactional
    public Pago consultarPago(Long pagoId) {

        return repositorio.findById(pagoId)
                .orElseThrow(() ->
                        new IllegalStateException("No se encontró el pago")
                );
    }

        public boolean intentarReclamarCobro(Long pagoId) {int filasActualizadas = repositorio.reclamarCobro(
            pagoId,
            EstadoPago.EN_PROCESO
        );

        return filasActualizadas == 1;
    }
}
