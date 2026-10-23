package co.edu.ucc.cooperativa.reservas.aplicacion.servicios;

import co.edu.ucc.cooperativa.reservas.dominio.Reserva;
import co.edu.ucc.cooperativa.reservas.dominio.EstadoReserva;
import co.edu.ucc.cooperativa.reservas.aplicacion.puertos.entrada.ApartarSillaUseCase;
import co.edu.ucc.cooperativa.reservas.aplicacion.puertos.salida.ReservaRepositoryPort;
import co.edu.ucc.cooperativa.reservas.aplicacion.puertos.salida.SillaCatalogoPort;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

/**
 * Servicio de Dominio para el caso de uso de Apartar Sillas.
 *
 * Arquitectura Hexagonal Pura:
 * - NO usa anotaciones de Spring Boot (@Service, @Component, @Autowired).
 * - NO usa Lombok ni dependencias de persistencia.
 * - Inyecta exclusivamente el puerto de salida ReservaRepositoryPort por constructor.
 */
public class ApartarSillaService implements ApartarSillaUseCase {

    private final ReservaRepositoryPort reservaRepository;
    private final SillaCatalogoPort sillaCatalogo;
    private final Clock reloj;

    public ApartarSillaService(ReservaRepositoryPort reservaRepository, SillaCatalogoPort sillaCatalogo, Clock reloj) {
        this.reservaRepository = Objects.requireNonNull(reservaRepository, "El puerto reservaRepository es obligatorio");
        this.sillaCatalogo = Objects.requireNonNull(sillaCatalogo, "El puerto sillaCatalogo es obligatorio");
        this.reloj = (reloj != null) ? reloj : Clock.systemDefaultZone();
    }

    @Override
    public Reserva apartarSilla(String viajeId, String sillaId, String pasajeroId) {
        if (viajeId == null || viajeId.isBlank()) {
            throw new IllegalArgumentException("El identificador de viaje es obligatorio");
        }
        if (sillaId == null || sillaId.isBlank()) {
            throw new IllegalArgumentException("El identificador de silla es obligatorio");
        }
        if (pasajeroId == null || pasajeroId.isBlank()) {
            throw new IllegalArgumentException("El identificador de pasajero es obligatorio");
        }

        LocalDateTime ahora = LocalDateTime.now(reloj);

        if (!sillaCatalogo.existeEnViaje(viajeId, sillaId)) {
            throw new IllegalArgumentException("La silla no existe en el viaje indicado");
        }

        // Regla de Negocio ADR-003: Evitar doble reserva sobre el mismo viaje mientras la silla esté ACTIVA
        // Crear la entidad Reserva con estado ACTIVA y ventana de 10 minutos
        Reserva nuevaReserva = Reserva.crear(viajeId, sillaId, pasajeroId, ahora);

        // Persistir a través del puerto de salida
        return reservaRepository.adquirirReserva(nuevaReserva, ahora);
    }

    @Override
    public Optional<Reserva> consultarReserva(String reservaId) {
        if (reservaId == null || reservaId.isBlank()) {
            return Optional.empty();
        }
        return reservaRepository.buscarPorId(reservaId.trim());
    }

    @Override
    public Reserva confirmarReserva(String reservaId) {
        if (reservaId == null || reservaId.isBlank()) {
            throw new IllegalArgumentException("El identificador de reserva es obligatorio");
        }

        Reserva reserva = reservaRepository.buscarPorId(reservaId.trim())
                .orElseThrow(() -> new IllegalArgumentException("No se encontró la reserva con ID: " + reservaId));

        LocalDateTime ahora = LocalDateTime.now(reloj);
        reserva.confirmar(ahora);

        Optional<Reserva> confirmada = reservaRepository.confirmarSiVigente(reserva.getId(), ahora);
        if (confirmada.isPresent()) {
            return confirmada.get();
        }

        // No guardar el agregado cargado anteriormente: pudo cambiar en Mongo.
        Reserva actual = reservaRepository.buscarPorId(reserva.getId())
                .orElseThrow(() -> new IllegalArgumentException("No se encontró la reserva con ID: " + reservaId));
        if (actual.getEstado() == EstadoReserva.CONFIRMADA) {
            return actual;
        }
        if (actual.getEstado() == EstadoReserva.EXPIRADA
                || actual.estaExpirada(ahora)) {
            actual.confirmar(actual.getFechaExpiracion());
        }
        throw new IllegalStateException("La reserva cambió mientras se confirmaba: " + reservaId);
    }
}
