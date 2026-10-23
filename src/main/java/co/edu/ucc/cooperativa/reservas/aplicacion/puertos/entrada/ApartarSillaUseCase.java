package co.edu.ucc.cooperativa.reservas.aplicacion.puertos.entrada;

import co.edu.ucc.cooperativa.reservas.dominio.Reserva;

import java.util.Optional;

/**
 * Puerto de Entrada (Driver Port) para el caso de uso de apartar sillas.
 * Contrato que define la operación de negocio solicitada según ADR-003.
 */
public interface ApartarSillaUseCase {

    /**
     * Aparta temporalmente una silla para un pasajero durante 10 minutos con estado ACTIVA
     * para evitar doble reserva sobre el mismo viaje.
     *
     * @param viajeId Identificador del viaje
     * @param sillaId Identificador o número de la silla
     * @param pasajeroId Identificador o documento del pasajero
     * @return La Reserva creada en estado ACTIVA con vencimiento a 10 minutos
     */
    Reserva apartarSilla(String viajeId, String sillaId, String pasajeroId);

    /**
     * Consulta una reserva por su identificador único.
     */
    Optional<Reserva> consultarReserva(String reservaId);

    /**
     * Confirma la reserva convirtiéndola en venta efectiva una vez realizado el pago.
     */
    Reserva confirmarReserva(String reservaId);
}
