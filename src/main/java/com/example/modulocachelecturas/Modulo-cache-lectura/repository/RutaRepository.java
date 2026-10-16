package com.example.modulocachelecturas.repository;

import com.example.modulocachelecturas.model.Ruta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RutaRepository extends JpaRepository<Ruta, Long> {
    Optional<Ruta> findByCodigoRuta(String codigoRuta);
}
