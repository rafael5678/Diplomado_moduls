package co.edu.ucc.cooperativa.reservas.adaptadores.entrada.rest;

import co.edu.ucc.cooperativa.reservas.dominio.modelo.ReservaExpiradaException;
import co.edu.ucc.cooperativa.reservas.dominio.modelo.SillaNoDisponibleException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Manejador global de excepciones para el controlador de Reservas de Cooperativa.
 * Mapea las excepciones de dominio a códigos de estado HTTP estándar.
 */
@RestControllerAdvice(basePackageClasses = ReservaController.class)
public class ReservaExceptionHandler {

    @ExceptionHandler(SillaNoDisponibleException.class)
    public ResponseEntity<Map<String, Object>> handleSillaNoDisponible(SillaNoDisponibleException ex) {
        return buildResponse(HttpStatus.CONFLICT, "SILLA_NO_DISPONIBLE", ex.getMessage());
    }

    @ExceptionHandler(ReservaExpiradaException.class)
    public ResponseEntity<Map<String, Object>> handleReservaExpirada(ReservaExpiradaException ex) {
        return buildResponse(HttpStatus.CONFLICT, "RESERVA_EXPIRADA", ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, "DATOS_INVALIDOS", ex.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException ex) {
        return buildResponse(HttpStatus.CONFLICT, "ESTADO_INVALIDO", ex.getMessage());
    }

    private ResponseEntity<Map<String, Object>> buildResponse(HttpStatus status, String codigo, String mensaje) {
        return ResponseEntity.status(status).body(Map.of(
                "timestamp", LocalDateTime.now(),
                "status", status.value(),
                "error", codigo,
                "mensaje", mensaje
        ));
    }
}
