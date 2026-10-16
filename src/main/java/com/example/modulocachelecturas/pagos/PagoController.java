
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

        String reservaId = (String) solicitud.get("reservaId");
        String documento = (String) solicitud.get("documento");

        Long valorEnPesos;

        try {
            valorEnPesos = Long.valueOf(
                    solicitud.get("valorEnPesos").toString()
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

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> manejarError(
            IllegalArgumentException error) {

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", error.getMessage()));
    }
}
