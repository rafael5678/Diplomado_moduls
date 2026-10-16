package com.example.modulocachelecturas.reservas.controller;

import com.example.modulocachelecturas.reservas.controller.ReservaDTOs.ErrorDTO;
import com.example.modulocachelecturas.reservas.service.ReservaExceptions.RecursoNoEncontradoException;
import com.example.modulocachelecturas.reservas.service.ReservaExceptions.ReservaNoModificableException;
import com.example.modulocachelecturas.reservas.service.ReservaExceptions.SillaNoDisponibleException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;

/**
 * TRADUCE LOS ERRORES DEL MÓDULO DE RESERVAS A RESPUESTAS HTTP.
 *
 * Solo aplica a los controladores del módulo de reservas (basePackageClasses),
 * para no cambiar el comportamiento del módulo de caché.
 */
@RestControllerAdvice(basePackageClasses = ReservaSillaController.class)
public class ReservaExceptionHandler {

    /**
     * 409: la silla ya la tiene otro pasajero. El front debe pedirle que elija otra.
     */
    @ExceptionHandler(SillaNoDisponibleException.class)
    public ResponseEntity<ErrorDTO> sillaNoDisponible(SillaNoDisponibleException e) {
        return error(HttpStatus.CONFLICT, "SILLA_NO_DISPONIBLE", e.getMessage());
    }

    /**
     * 409: la reserva venció, se liberó o ya está pagada.
     */
    @ExceptionHandler(ReservaNoModificableException.class)
    public ResponseEntity<ErrorDTO> reservaNoModificable(ReservaNoModificableException e) {
        return error(HttpStatus.CONFLICT, "RESERVA_NO_MODIFICABLE", e.getMessage());
    }

    /**
     * 404: el viaje, la silla o la reserva no existen.
     */
    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<ErrorDTO> noEncontrado(RecursoNoEncontradoException e) {
        return error(HttpStatus.NOT_FOUND, "NO_ENCONTRADO", e.getMessage());
    }

    /**
     * 400: faltan datos en la solicitud.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorDTO> datosInvalidos(IllegalArgumentException e) {
        return error(HttpStatus.BAD_REQUEST, "DATOS_INVALIDOS", e.getMessage());
    }

    private ResponseEntity<ErrorDTO> error(HttpStatus status, String codigo, String mensaje) {
        ErrorDTO cuerpo = ErrorDTO.builder()
                .status(status.value())
                .codigo(codigo)
                .mensaje(mensaje)
                .fecha(LocalDateTime.now())
                .build();
        return ResponseEntity.status(status).body(cuerpo);
    }
}
