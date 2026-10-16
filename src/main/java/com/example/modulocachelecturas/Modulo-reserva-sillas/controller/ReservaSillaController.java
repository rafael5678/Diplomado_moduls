package com.example.modulocachelecturas.reservas.controller;

import com.example.modulocachelecturas.reservas.controller.ReservaDTOs.ApartarSillaRequest;
import com.example.modulocachelecturas.reservas.controller.ReservaDTOs.ReservaDTO;
import com.example.modulocachelecturas.reservas.model.ReservaSilla;
import com.example.modulocachelecturas.reservas.service.ReservaSillaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * CONTROLADOR REST - MÓDULO DE RESERVAS (ADR-004: cada silla se aparta y se vende una sola vez).
 *
 * Flujo normal:
 * 1. El pasajero ve el mapa:        GET  /api/v1/viajes/{id}/sillas      (módulo de caché)
 * 2. Toca una silla:                POST /api/v1/reservas                (queda apartada 10 min)
 * 3. Paga y Pagos confirma:         POST /api/v1/reservas/{id}/confirmar (silla vendida)
 *    o se arrepiente:               DELETE /api/v1/reservas/{id}         (silla liberada)
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/reservas")
@RequiredArgsConstructor
public class ReservaSillaController {

    private final ReservaSillaService reservaSillaService;
    private final Clock reloj;

    /**
     * APARTAR UNA SILLA.
     * Responde 201 si quedó apartada, o 409 si otro pasajero la tiene.
     */
    @PostMapping
    public ResponseEntity<ReservaDTO> apartar(@RequestBody ApartarSillaRequest request) {
        log.info("[HTTP POST] Apartar silla {} del viaje {} para pasajero {}",
                request.getNumeroSilla(), request.getViajeId(), request.getPasajeroId());
        ReservaSilla reserva = reservaSillaService.apartar(
                request.getViajeId(), request.getNumeroSilla(), request.getPasajeroId());
        return ResponseEntity.status(HttpStatus.CREATED).body(aDTO(reserva));
    }

    /**
     * CONSULTAR UNA RESERVA (por ejemplo, para mostrar el tiempo que le queda al pasajero).
     */
    @GetMapping("/{id}")
    public ResponseEntity<ReservaDTO> consultar(@PathVariable("id") Long id) {
        return ResponseEntity.ok(aDTO(reservaSillaService.consultar(id)));
    }

    /**
     * CONFIRMAR LA VENTA. Lo llama el módulo de Pagos cuando la pasarela aprueba el cobro.
     * Si se llama dos veces con la misma reserva, la segunda responde igual sin cambiar nada.
     * Responde 409 si la reserva ya venció: Pagos debe devolver el dinero (ADR-005).
     */
    @PostMapping("/{id}/confirmar")
    public ResponseEntity<ReservaDTO> confirmar(@PathVariable("id") Long id) {
        log.info("[HTTP POST] Confirmar venta de la reserva {}", id);
        return ResponseEntity.ok(aDTO(reservaSillaService.confirmar(id)));
    }

    /**
     * CANCELAR UNA RESERVA antes de pagar. La silla vuelve a estar disponible.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ReservaDTO> cancelar(@PathVariable("id") Long id) {
        log.info("[HTTP DELETE] Cancelar reserva {}", id);
        return ResponseEntity.ok(aDTO(reservaSillaService.cancelar(id)));
    }

    private ReservaDTO aDTO(ReservaSilla reserva) {
        return ReservaDTO.desde(reserva, LocalDateTime.now(reloj));
    }
}
