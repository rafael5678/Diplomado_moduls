package com.example.modulocachelecturas.repository;

import com.example.modulocachelecturas.model.Ruta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * REPOSITORIO JPA PARA LA ENTIDAD 'Ruta'.
 * Permite consultar rutas de transporte registradas en PostgreSQL.
 */
@Repository
public interface RutaRepository extends JpaRepository<Ruta, Long> {

    /**
     * Búsqueda de ruta por su código único de identificación (ej: "RUTA-BOG-MED").
     */
    Optional<Ruta> findByCodigoRuta(String codigoRuta);
}
