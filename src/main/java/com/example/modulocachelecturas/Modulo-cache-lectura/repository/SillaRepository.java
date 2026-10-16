package com.example.modulocachelecturas.repository;

import com.example.modulocachelecturas.model.EstadoSilla;
import com.example.modulocachelecturas.model.Silla;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * REPOSITORIO JPA PARA LA ENTIDAD 'Silla'.
 * Provee consultas específicas sobre las sillas asignadas a un viaje en PostgreSQL.
 */
@Repository
public interface SillaRepository extends JpaRepository<Silla, Long> {

    /**
     * Obtiene todas las sillas de un viaje ordenadas correlativamente (1, 2, 3... 40).
     */
    List<Silla> findByViajeIdOrderByNumeroSillaAsc(Long viajeId);

    /**
     * Conteo rápido de sillas filtradas por estado (DISPONIBLE, RESERVADA, OCUPADA).
     */
    long countByViajeIdAndEstado(Long viajeId, EstadoSilla estado);
}
