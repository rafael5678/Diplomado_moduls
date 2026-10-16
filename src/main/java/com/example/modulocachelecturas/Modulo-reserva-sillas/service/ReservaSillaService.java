package com.example.modulocachelecturas.reservas.service;

import com.example.modulocachelecturas.model.EstadoSilla;
import com.example.modulocachelecturas.model.Silla;
import com.example.modulocachelecturas.reservas.model.EstadoReserva;
import com.example.modulocachelecturas.reservas.model.ReservaSilla;
import com.example.modulocachelecturas.reservas.repository.ReservaSillaRepository;
import com.example.modulocachelecturas.reservas.repository.SillaReservaRepository;
import com.example.modulocachelecturas.reservas.service.ReservaExceptions.RecursoNoEncontradoException;
import com.example.modulocachelecturas.reservas.service.ReservaExceptions.ReservaNoModificableException;
import com.example.modulocachelecturas.reservas.service.ReservaExceptions.SillaNoDisponibleException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * SERVICIO CENTRAL DEL MÓDULO DE RESERVAS (ADR-004: cada silla se aparta y se vende una sola vez).
 *
 * Responsabilidad en la arquitectura:
 * - Apartar la silla 10 minutos mientras el pasajero paga.
 * - Garantizar el ASR 2: 0 sillas vendidas dos veces, aunque varios servidores atiendan a la vez (ADR-001).
 * - Pasar la silla a VENDIDA cuando el módulo de Pagos confirma el cobro (ADR-005).
 * - Liberar la silla si el pasajero cancela o si se vencen los 10 minutos.
 *
 * Quién decide si una silla está libre es la BASE DE DATOS (restricción UNIQUE sobre 'llave_activa'),
 * no la caché: el mapa en Redis puede estar atrasado unos segundos.
 */
@Slf4j
@Service
public class ReservaSillaService {

    private static final int TAMANO_LOTE_VENCIDAS = 500;

    private final ReservaSillaRepository reservaRepository;
    private final SillaReservaRepository sillaRepository;
    private final TransactionTemplate transaccion;
    private final ApplicationEventPublisher eventos;
    private final Clock reloj;
    private final long minutosReserva;

    public ReservaSillaService(ReservaSillaRepository reservaRepository,
                               SillaReservaRepository sillaRepository,
                               PlatformTransactionManager transactionManager,
                               ApplicationEventPublisher eventos,
                               Clock reloj,
                               @Value("${reservas.tiempo-reserva-minutos:10}") long minutosReserva) {
        this.reservaRepository = reservaRepository;
        this.sillaRepository = sillaRepository;
        this.transaccion = new TransactionTemplate(transactionManager);
        this.eventos = eventos;
        this.reloj = reloj;
        this.minutosReserva = minutosReserva;
    }

    /**
     * APARTAR UNA SILLA (el pasajero toca la silla en el mapa).
     *
     * Pasos:
     * 1. Verifica que la silla exista y esté DISPONIBLE.
     * 2. Inserta la reserva con 'llave_activa' = "viajeId:numeroSilla" (restricción UNIQUE).
     * 3. Cambia la silla a RESERVADA y avisa a la caché.
     *
     * Si dos pasajeros llegan al mismo tiempo, los dos pueden pasar el paso 1,
     * pero la base de datos solo deja pasar UNA inserción en el paso 2.
     * Al segundo se le responde que la silla ya está ocupada.
     */
    public ReservaSilla apartar(Long viajeId, Integer numeroSilla, String pasajeroId) {
        validarDatos(viajeId, numeroSilla, pasajeroId);
        try {
            return transaccion.execute(estado -> apartarEnTransaccion(viajeId, numeroSilla, pasajeroId.trim()));
        } catch (DataIntegrityViolationException e) {
            // La restricción UNIQUE rechazó la inserción: otro pasajero apartó la silla primero
            log.info("[Reserva Rechazada] Silla {} del viaje {} ya fue apartada por otro pasajero", numeroSilla, viajeId);
            throw new SillaNoDisponibleException(
                    "La silla " + numeroSilla + " ya está ocupada. Por favor elige otra.");
        }
    }

    private ReservaSilla apartarEnTransaccion(Long viajeId, Integer numeroSilla, String pasajeroId) {
        Silla silla = sillaRepository.findByViajeIdAndNumeroSilla(viajeId, numeroSilla)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "La silla " + numeroSilla + " no existe en el viaje " + viajeId));

        if (silla.getEstado() != EstadoSilla.DISPONIBLE) {
            throw new SillaNoDisponibleException(
                    "La silla " + numeroSilla + " ya está ocupada. Por favor elige otra.");
        }

        LocalDateTime ahora = LocalDateTime.now(reloj);
        ReservaSilla reserva = ReservaSilla.builder()
                .viajeId(viajeId)
                .numeroSilla(numeroSilla)
                .pasajeroId(pasajeroId)
                .estado(EstadoReserva.APARTADA)
                .llaveActiva(ReservaSilla.llaveDe(viajeId, numeroSilla))
                .creadaEn(ahora)
                .venceEn(ahora.plusMinutes(minutosReserva))
                .actualizadaEn(ahora)
                .build();

        // saveAndFlush envía el INSERT de inmediato: si la llave ya existe, falla aquí mismo
        ReservaSilla guardada = reservaRepository.saveAndFlush(reserva);
        sillaRepository.actualizarEstado(viajeId, numeroSilla, EstadoSilla.RESERVADA);
        eventos.publishEvent(new SillaCambioEstadoEvent(viajeId, numeroSilla, "SILLA_APARTADA"));

        log.info("[Silla Apartada] Viaje {} silla {} para pasajero {} hasta {}",
                viajeId, numeroSilla, pasajeroId, guardada.getVenceEn());
        return guardada;
    }

    /**
     * CONFIRMAR LA VENTA (lo llama el módulo de Pagos cuando la pasarela aprueba el cobro).
     *
     * - Si la reserva está APARTADA y no ha vencido: pasa a VENDIDA y la silla a OCUPADA.
     * - Si ya estaba VENDIDA: responde igual, sin cambiar nada (la pasarela puede avisar dos veces).
     * - Si ya se liberó o venció: lanza error para que Pagos devuelva el dinero (ADR-005).
     */
    public ReservaSilla confirmar(Long reservaId) {
        return transaccion.execute(estado -> {
            ReservaSilla reserva = buscarReserva(reservaId);
            LocalDateTime ahora = LocalDateTime.now(reloj);

            int cambiadas = reservaRepository.marcarVendidaSiVigente(
                    reservaId, ahora, EstadoReserva.APARTADA, EstadoReserva.VENDIDA);

            if (cambiadas == 0) {
                // Se vuelve a leer: otra confirmación pudo venderla un instante antes
                ReservaSilla actual = buscarReserva(reservaId);
                if (actual.getEstado() == EstadoReserva.VENDIDA) {
                    log.info("[Confirmacion Repetida] Reserva {} ya estaba vendida; no se cambia nada", reservaId);
                    return actual;
                }
                throw new ReservaNoModificableException(
                        "La reserva " + reservaId + " venció o fue liberada. El pago debe devolverse.");
            }

            sillaRepository.actualizarEstado(reserva.getViajeId(), reserva.getNumeroSilla(), EstadoSilla.OCUPADA);
            eventos.publishEvent(new SillaCambioEstadoEvent(
                    reserva.getViajeId(), reserva.getNumeroSilla(), "SILLA_VENDIDA"));

            log.info("[Silla Vendida] Reserva {} (viaje {} silla {})",
                    reservaId, reserva.getViajeId(), reserva.getNumeroSilla());
            return buscarReserva(reservaId);
        });
    }

    /**
     * CANCELAR UNA RESERVA (el pasajero se arrepiente antes de pagar).
     *
     * - Si está APARTADA: la libera y la silla vuelve a DISPONIBLE.
     * - Si ya estaba LIBERADA: responde igual, sin cambiar nada.
     * - Si ya está VENDIDA: no se puede cancelar por aquí (sería una devolución de tiquete).
     */
    public ReservaSilla cancelar(Long reservaId) {
        return transaccion.execute(estado -> {
            ReservaSilla reserva = buscarReserva(reservaId);
            liberarSiApartada(reserva, "RESERVA_CANCELADA");

            // Se vuelve a leer: si no se liberó, puede ser porque el pago la vendió un instante antes
            ReservaSilla actual = buscarReserva(reservaId);
            if (actual.getEstado() == EstadoReserva.VENDIDA) {
                throw new ReservaNoModificableException(
                        "La reserva " + reservaId + " ya está pagada; no se puede cancelar desde aquí.");
            }
            return actual;
        });
    }

    /**
     * LIBERAR RESERVAS VENCIDAS (lo llama el proceso programado cada minuto).
     *
     * Toma las reservas APARTADAS cuyo tiempo ya pasó, por lotes, y libera cada una
     * en su propia transacción. Si hay varios servidores corriendo este proceso a la vez,
     * no hay problema: el UPDATE condicional hace que cada reserva se libere una sola vez.
     *
     * @return cuántas reservas se liberaron
     */
    public int liberarVencidas() {
        LocalDateTime ahora = LocalDateTime.now(reloj);
        List<ReservaSilla> vencidas = reservaRepository.buscarVencidas(
                EstadoReserva.APARTADA, ahora, PageRequest.of(0, TAMANO_LOTE_VENCIDAS));

        int liberadas = 0;
        for (ReservaSilla reserva : vencidas) {
            try {
                Boolean ok = transaccion.execute(estado -> liberarSiApartada(reserva, "RESERVA_VENCIDA"));
                if (Boolean.TRUE.equals(ok)) {
                    liberadas++;
                }
            } catch (RuntimeException e) {
                // Una reserva con problema no debe detener la liberación de las demás
                log.error("[Liberacion Fallida] Reserva {}: {}", reserva.getId(), e.getMessage());
            }
        }
        return liberadas;
    }

    /**
     * Cuenta las reservas que siguen APARTADAS más de 'minutosAtraso' después de vencer.
     * Si es mayor a 0, el proceso de liberación probablemente se detuvo.
     */
    public long contarReservasAtrasadas(long minutosAtraso) {
        LocalDateTime limite = LocalDateTime.now(reloj).minusMinutes(minutosAtraso);
        return reservaRepository.countByEstadoAndVenceEnBefore(EstadoReserva.APARTADA, limite);
    }

    /**
     * Consulta una reserva por su id.
     */
    public ReservaSilla consultar(Long reservaId) {
        return buscarReserva(reservaId);
    }

    // ------------------------------------------------------------------------
    // Métodos internos
    // ------------------------------------------------------------------------

    /**
     * Libera la reserva solo si sigue APARTADA. Debe llamarse dentro de una transacción.
     *
     * @return true si este llamado la liberó; false si otro proceso ya la había cambiado
     */
    private boolean liberarSiApartada(ReservaSilla reserva, String motivo) {
        int cambiadas = reservaRepository.marcarLiberadaSiApartada(
                reserva.getId(), LocalDateTime.now(reloj), EstadoReserva.APARTADA, EstadoReserva.LIBERADA);
        if (cambiadas == 0) {
            return false; // Ya estaba liberada o se vendió en el último segundo
        }
        sillaRepository.actualizarEstado(reserva.getViajeId(), reserva.getNumeroSilla(), EstadoSilla.DISPONIBLE);
        eventos.publishEvent(new SillaCambioEstadoEvent(reserva.getViajeId(), reserva.getNumeroSilla(), motivo));
        log.info("[Silla Liberada] Reserva {} (viaje {} silla {}) motivo: {}",
                reserva.getId(), reserva.getViajeId(), reserva.getNumeroSilla(), motivo);
        return true;
    }

    private ReservaSilla buscarReserva(Long reservaId) {
        return reservaRepository.findById(reservaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("La reserva " + reservaId + " no existe"));
    }

    private void validarDatos(Long viajeId, Integer numeroSilla, String pasajeroId) {
        if (viajeId == null || numeroSilla == null || pasajeroId == null || pasajeroId.isBlank()) {
            throw new IllegalArgumentException("viajeId, numeroSilla y pasajeroId son obligatorios");
        }
        if (numeroSilla < 1) {
            throw new IllegalArgumentException("numeroSilla debe ser mayor a 0");
        }
    }
}
