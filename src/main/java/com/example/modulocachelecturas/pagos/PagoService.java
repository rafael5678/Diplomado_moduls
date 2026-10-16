
package com.example.modulocachelecturas.pagos;

import java.util.Objects;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class PagoService {

    private final PagoRepository repositorio;
    private final RegistroPagoService registro;
    private final PasarelaSimulada pasarela;

    public PagoService(
            PagoRepository repositorio,
            RegistroPagoService registro,
            PasarelaSimulada pasarela) {

        this.repositorio = repositorio;
        this.registro = registro;
        this.pasarela = pasarela;
    }

    public Pago buscarPagoExistente(String llaveIdempotencia) {
        return repositorio.findByLlaveIdempotencia(llaveIdempotencia)
                .orElse(null);
    }

    public Pago crearPago(
            String llaveIdempotencia,
            String reservaId,
            String documento,
            Long valorEnPesos) {

        validarLlave(llaveIdempotencia);

        return registro.registrar(
                llaveIdempotencia,
                reservaId,
                documento,
                valorEnPesos
        );
    }

    private void validarLlave(String llaveIdempotencia) {
        if (llaveIdempotencia == null || llaveIdempotencia.isBlank()) {
            throw new IllegalArgumentException(
                    "La llave de idempotencia es obligatoria"
            );
        }
    }

    public Pago procesarCobro(
            String llaveIdempotencia,
            String reservaId,
            String documento,
            Long valorEnPesos) {

        validarLlave(llaveIdempotencia);

        Pago existente = buscarPagoExistente(llaveIdempotencia);

        if (existente != null) {
            validarMismaSolicitud(
                    existente,
                    reservaId,
                    documento,
                    valorEnPesos
            );

            return existente;
        }

        Pago pago;

        try {
            pago = crearPago(
                    llaveIdempotencia,
                    reservaId,
                    documento,
                    valorEnPesos
            );

        } catch (DataIntegrityViolationException error) {

            Pago pagoOriginal = buscarPagoExistente(llaveIdempotencia);

            if (pagoOriginal != null) {
                validarMismaSolicitud(
                        pagoOriginal,
                        reservaId,
                        documento,
                        valorEnPesos
                );

                return pagoOriginal;
            }

            throw error;
        }

        return ejecutarCobro(pago);
    }

    private void validarMismaSolicitud(
            Pago pago,
            String reservaId,
            String documento,
            Long valorEnPesos) {

        if (!Objects.equals(pago.getReservaId(), reservaId)
                || !Objects.equals(pago.getDocumento(), documento)
                || !Objects.equals(pago.getValorEnPesos(), valorEnPesos)) {

            throw new ConflictoIdempotenciaException(
                    "La llave de idempotencia ya pertenece a otra solicitud"
            );
        }
    }

    public Pago ejecutarCobro(Pago pago) {

        if (pago.getEstado() != EstadoPago.EN_PROCESO) {
            return pago;
        }

        boolean puedeCobrar = registro.intentarReclamarCobro(
                pago.getId()
        );

        if (!puedeCobrar) {
            return registro.consultarPago(pago.getId());
        }

        EstadoPago resultado = pasarela.cobrar(
                pago.getReferencia(),
                pago.getValorEnPesos()
        );

        return registro.actualizarEstado(
                pago.getId(),
                resultado
        );
    }
}
