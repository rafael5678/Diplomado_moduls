
package com.example.modulocachelecturas.pagos;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/cobros")
public class PagoController {

    private final PagoService pagoService;

    public PagoController(PagoService pagoService) {
        this.pagoService = pagoService;
    }

    @PostMapping
    public ResponseEntity<RespuestaPago> crearCobro(
            @RequestHeader(
                    value = "Idempotency-Key",
                    required = false
            ) String llaveIdempotencia,
            @RequestBody Map<String, Object> solicitud) {

        if (llaveIdempotencia == null
                || llaveIdempotencia.isBlank()) {
            throw new IllegalArgumentException(
                    "La llave de idempotencia es obligatoria"
            );
        }

        String reservaId = obtenerTexto(solicitud, "reservaId");
        String documento = obtenerTexto(solicitud, "documento");

        Long valorEnPesos;

        try {
            Object valor = solicitud.get("valorEnPesos");

            valorEnPesos = Long.valueOf(
                    valor.toString()
            );

        } catch (RuntimeException error) {
            throw new IllegalArgumentException(
                    "El valorEnPesos debe ser un número entero"
            );
        }

        if (reservaId == null || reservaId.isBlank()
                || documento == null || documento.isBlank()
                || valorEnPesos <= 0) {
            throw new IllegalArgumentException(
                    "Los datos del cobro son obligatorios y el valor debe ser positivo"
            );
        }

        Pago pago = pagoService.procesarCobro(
                llaveIdempotencia,
                reservaId,
                documento,
                valorEnPesos
        );

        return ResponseEntity.ok(
                RespuestaPago.desde(pago)
        );
    }

    private String obtenerTexto(
            Map<String, Object> solicitud,
            String campo) {

        Object valor = solicitud.get(campo);

        if (!(valor instanceof String)) {
            throw new IllegalArgumentException(
                    "El campo " + campo + " debe ser un texto"
            );
        }

        return (String) valor;
    }

    @ExceptionHandler(ConflictoIdempotenciaException.class)
    public ResponseEntity<Map<String, String>> manejarConflicto(
            ConflictoIdempotenciaException error) {

        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", error.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> manejarError(
            IllegalArgumentException error) {

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", error.getMessage()));
    }
}
