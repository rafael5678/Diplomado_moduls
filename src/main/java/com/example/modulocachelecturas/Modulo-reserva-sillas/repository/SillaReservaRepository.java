package com.example.modulocachelecturas.reservas.repository;

import com.example.modulocachelecturas.model.EstadoSilla;
import com.example.modulocachelecturas.model.Silla;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * ACCESO DEL MÓDULO DE RESERVAS A LA TABLA 'sillas'.
 *
 * El módulo de caché (ADR de caché) solo LEE las sillas para armar el mapa.
 * El módulo de reservas es el único que CAMBIA su estado (DISPONIBLE, RESERVADA, OCUPADA),
 * y después avisa a la caché para que borre la copia del mapa de ese viaje.
 */
@org.springframework.stereotype.Repository
public interface SillaReservaRepository extends Repository<Silla, Long> {

    /**
     * Busca una silla por viaje y número visible (ej: viaje 1, silla 12).
     */
    Optional<Silla> findByViajeIdAndNumeroSilla(Long viajeId, Integer numeroSilla);

    /**
     * Cambia el estado visible de la silla en el mapa.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Silla s SET s.estado = :estado WHERE s.viaje.id = :viajeId AND s.numeroSilla = :numeroSilla")
    int actualizarEstado(@Param("viajeId") Long viajeId,
                         @Param("numeroSilla") Integer numeroSilla,
                         @Param("estado") EstadoSilla estado);
}
