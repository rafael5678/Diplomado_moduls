package com.example.modulocachelecturas.repository;

import com.example.modulocachelecturas.model.Viaje;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ViajeRepository extends JpaRepository<Viaje, Long> {

    @Query("SELECT v FROM Viaje v LEFT JOIN FETCH v.ruta LEFT JOIN FETCH v.sillas WHERE v.id = :id")
    Optional<Viaje> findByIdWithSillas(@Param("id") Long id);

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
