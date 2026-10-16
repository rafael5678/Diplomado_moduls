package com.example.modulocachelecturas.reservas.service;

import com.example.modulocachelecturas.service.CacheSillasService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * PUENTE ENTRE EL MÓDULO DE RESERVAS Y EL MÓDULO DE CACHÉ.
 *
 * Cuando una silla se aparta, se vende o se libera, el mapa guardado en Redis queda viejo.
 * Este listener le pide al módulo de caché que borre el mapa de ese viaje
 * (CacheSillasService.evictMapaSillas), para que el siguiente pasajero lo vea actualizado.
 *
 * Se ejecuta DESPUÉS del commit (AFTER_COMMIT): si la transacción falla, la caché no se toca,
 * y nadie lee de la base de datos un cambio que todavía no se ha guardado.
 *
 * Si Redis está caído, la reserva igual queda guardada: el mapa se refresca solo
 * cuando venza su TTL, y la base de datos sigue siendo la que decide (ADR-004).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InvalidacionCacheListener {

    private final CacheSillasService cacheSillasService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void alCambiarEstadoSilla(SillaCambioEstadoEvent evento) {
        try {
            cacheSillasService.evictMapaSillas(evento.viajeId(), evento.motivo());
        } catch (RuntimeException e) {
            log.warn("[Invalidacion Cache] No se pudo borrar el mapa del viaje {} ({}): {}",
                    evento.viajeId(), evento.motivo(), e.getMessage());
        }
    }
}
