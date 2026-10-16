package com.example.modulocachelecturas.reservas.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * PROCESO PROGRAMADO: libera las sillas cuyo tiempo de reserva ya venció (ADR-004).
 *
 * - Corre cada minuto (configurable con 'reservas.liberacion.intervalo-ms').
 * - Si un pasajero apartó una silla y no pagó en 10 minutos, la silla vuelve a DISPONIBLE.
 * - Con varios servidores (ADR-001) cada uno corre este proceso; no hay problema porque
 *   el UPDATE condicional hace que cada reserva se libere una sola vez.
 * - Riesgo del ADR: "el proceso de liberación se detiene". Mitigación: si hay reservas
 *   vencidas hace más de 15 minutos, se escribe una ALERTA en el log.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LiberadorReservasVencidas {

    private final ReservaSillaService reservaSillaService;

    @Value("${reservas.alerta.atraso-minutos:15}")
    private long minutosAtrasoAlerta;

    @Scheduled(
            fixedDelayString = "${reservas.liberacion.intervalo-ms:60000}",
            initialDelayString = "${reservas.liberacion.intervalo-ms:60000}"
    )
    public void ejecutar() {
        try {
            int liberadas = reservaSillaService.liberarVencidas();
            if (liberadas > 0) {
                log.info("[Liberador] Se liberaron {} sillas con reserva vencida", liberadas);
            }

            long atrasadas = reservaSillaService.contarReservasAtrasadas(minutosAtrasoAlerta);
            if (atrasadas > 0) {
                log.error("[ALERTA Reservas] Hay {} reservas vencidas hace más de {} minutos sin liberar",
                        atrasadas, minutosAtrasoAlerta);
            }
        } catch (RuntimeException e) {
            // Un error no debe detener las siguientes ejecuciones del proceso
            log.error("[Liberador] Error liberando reservas vencidas: {}", e.getMessage(), e);
        }
    }
}
