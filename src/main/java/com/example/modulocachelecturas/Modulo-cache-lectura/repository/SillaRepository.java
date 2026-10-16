package com.example.modulocachelecturas.repository;

import com.example.modulocachelecturas.model.EstadoSilla;
import com.example.modulocachelecturas.model.Silla;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SillaRepository extends JpaRepository<Silla, Long> {
    List<Silla> findByViajeIdOrderByNumeroSillaAsc(Long viajeId);
    long countByViajeIdAndEstado(Long viajeId, EstadoSilla estado);
}
