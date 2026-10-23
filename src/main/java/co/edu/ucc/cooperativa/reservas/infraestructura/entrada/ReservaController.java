package co.edu.ucc.cooperativa.reservas.infraestructura.entrada;

import co.edu.ucc.cooperativa.reservas.dominio.Reserva;
import co.edu.ucc.cooperativa.reservas.aplicacion.puertos.entrada.ApartarSillaUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Adaptador de Entrada (REST Controller) para el módulo de Reservas.
 *
 * Principio de Arquitectura Hexagonal:
 * Inyecta EXCLUSIVAMENTE el Puerto de Entrada (ApartarSillaUseCase).
 * Mantiene la capa web completamente desacoplada de la implementación y persistencia.
 */
@RestController
@RequestMapping("/api/v1/reservas")
public class ReservaController {

    private final ApartarSillaUseCase apartarSillaUseCase;

    public ReservaController(ApartarSillaUseCase apartarSillaUseCase) {
        this.apartarSillaUseCase = apartarSillaUseCase;
    }

    /**
     * Endpoint para apartar una silla de un viaje temporalmente (10 min con estado ACTIVA).
     * POST /api/v1/reservas
     */
    @PostMapping
    public ResponseEntity<ReservaResponseDto> apartarSilla(@RequestBody ReservaRequestDto request) {
        Reserva reserva = apartarSillaUseCase.apartarSilla(
                request.viajeId(),
                request.sillaId(),
                request.pasajeroId()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(ReservaResponseDto.fromDomain(reserva));
    }

    /**
     * Endpoint para confirmar una reserva activa una vez realizado el pago.
     * POST /api/v1/reservas/{id}/confirmar
     */
    @PostMapping("/{id}/confirmar")
    public ResponseEntity<ReservaResponseDto> confirmarReserva(@PathVariable("id") String id) {
        Reserva confirmada = apartarSillaUseCase.confirmarReserva(id);
        return ResponseEntity.ok(ReservaResponseDto.fromDomain(confirmada));
    }

    /**
     * Endpoint para consultar el estado de una reserva por ID.
     * GET /api/v1/reservas/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<ReservaResponseDto> consultarReserva(@PathVariable("id") String id) {
        return apartarSillaUseCase.consultarReserva(id)
                .map(ReservaResponseDto::fromDomain)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
