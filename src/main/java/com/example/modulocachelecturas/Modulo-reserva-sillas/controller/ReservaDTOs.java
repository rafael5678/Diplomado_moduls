package com.example.modulocachelecturas.reservas.controller;

import com.example.modulocachelecturas.reservas.model.EstadoReserva;
import com.example.modulocachelecturas.reservas.model.ReservaSilla;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * CONTRATOS DE DATOS (DTOs) DEL MÓDULO DE RESERVAS (ADR-004).
 *
 * Igual que en el módulo de caché, se agrupan en un solo archivo.
 */
public class ReservaDTOs {

    /**
     * Cuerpo de la solicitud para apartar una silla.
     * POST /api/v1/reservas
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ApartarSillaRequest {
        private Long viajeId;       // Viaje elegido
        private Integer numeroSilla; // Silla elegida en el mapa
        private String pasajeroId;   // Documento o id del pasajero
    }

    /**
     * Respuesta con el estado de una reserva.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ReservaDTO {
        private Long reservaId;
        private Long viajeId;
        private Integer numeroSilla;
        private String pasajeroId;
        private EstadoReserva estado; // APARTADA, VENDIDA o LIBERADA

        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime creadaEn;

        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime venceEn;

        private long segundosRestantes; // Tiempo que le queda al pasajero para pagar (0 si no aplica)

        public static ReservaDTO desde(ReservaSilla reserva, LocalDateTime ahora) {
            long restantes = 0;
            if (reserva.getEstado() == EstadoReserva.APARTADA && reserva.getVenceEn().isAfter(ahora)) {
                restantes = Duration.between(ahora, reserva.getVenceEn()).getSeconds();
            }
            return ReservaDTO.builder()
                    .reservaId(reserva.getId())
                    .viajeId(reserva.getViajeId())
                    .numeroSilla(reserva.getNumeroSilla())
                    .pasajeroId(reserva.getPasajeroId())
                    .estado(reserva.getEstado())
                    .creadaEn(reserva.getCreadaEn())
                    .venceEn(reserva.getVenceEn())
                    .segundosRestantes(restantes)
                    .build();
        }
    }

    /**
     * Respuesta de error con el mismo formato para 400, 404 y 409.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ErrorDTO {
        private int status;      // Código HTTP
        private String codigo;   // SILLA_NO_DISPONIBLE, NO_ENCONTRADO, RESERVA_NO_MODIFICABLE, DATOS_INVALIDOS
        private String mensaje;  // Mensaje para mostrar al pasajero

        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime fecha;
    }
}
