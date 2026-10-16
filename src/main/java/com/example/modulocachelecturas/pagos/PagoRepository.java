
package com.example.modulocachelecturas.pagos;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface PagoRepository extends JpaRepository<Pago, Long> {

    Optional<Pago> findByLlaveIdempotencia(String llaveIdempotencia);

    Optional<Pago> findByReferencia(String referencia);

    @Modifying
    @Transactional
    @Query("""
        UPDATE Pago p
        SET p.cobroIniciado = true,
            p.version = p.version + 1
        WHERE p.id = :pagoId
          AND p.cobroIniciado = false
          AND p.estado = :estado
        """)
    int reclamarCobro(
            @Param("pagoId") Long pagoId,
            @Param("estado") EstadoPago estado
    );
}
