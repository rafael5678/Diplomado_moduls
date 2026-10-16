package com.example.modulocachelecturas.repository;

import com.example.modulocachelecturas.model.Viaje;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * REPOSITORIO JPA PARA LA TABLA 'viajes' (PostgreSQL).
 *
 * Incluye optimizaciones de rendimiento con 'JOIN FETCH' para erradicar el
 * problema de consultas N+1 en la base de datos relacional.
 */
@Repository
public interface ViajeRepository extends JpaRepository<Viaje, Long> {

    /**
     * Consulta optimizada con 'JOIN FETCH':
     * En lugar de hacer 1 consulta para el viaje y luego 40 consultas individuales para cada silla (problema N+1),
     * une las tablas 'viajes', 'rutas' y 'sillas' en un único SELECT de alta velocidad.
     */
    @Query("SELECT v FROM Viaje v LEFT JOIN FETCH v.ruta LEFT JOIN FETCH v.sillas WHERE v.id = :id")
    Optional<Viaje> findByIdWithSillas(@Param("id") Long id);

    /**
     * Búsqueda dinámica de viajes disponibles según origen, destino y fecha.
     * Solo retorna viajes en estado 'PROGRAMADO' ordenados cronológicamente.
     */
    @Query("SELECT v FROM Viaje v JOIN FETCH v.ruta r " +
           "WHERE (:origen IS NULL OR LOWER(r.origen) LIKE LOWER(CONCAT('%', :origen, '%'))) " +
           "AND (:destino IS NULL OR LOWER(r.destino) LIKE LOWER(CONCAT('%', :destino, '%'))) " +
           "AND (:fecha IS NULL OR v.fechaSalida = :fecha) " +
           "AND v.estado = 'PROGRAMADO' " +
           "ORDER BY v.fechaSalida ASC, v.horaSalida ASC")
    List<Viaje> findViajesDisponibles(
            @Param("origen") String origen,
            @Param("destino") String destino,
            @Param("fecha") LocalDate fecha
    );
}
