
package com.example.modulocachelecturas.pagos.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "pagos",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_pagos_idempotencia",
        columnNames = "llave_idempotencia"
    )
)
public class Pago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "llave_idempotencia", nullable = false, unique = true)
    private String llaveIdempotencia;

    @Column(nullable = false)
    private String reservaId;

    @Column(nullable = false)
    private String documento;

    @Column(nullable = false)
    private Long valorEnPesos;

    @Column(nullable = false, unique = true)
    private String referencia;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoPago estado;

    @Column(nullable = false)
    private LocalDateTime fechaCreacion;

    @Column(name = "cobro_iniciado", nullable = false)
    private boolean cobroIniciado = false;

    @Version
    private Long version;

    protected Pago() {
    }

    public Pago(String llaveIdempotencia, String reservaId,
                String documento, Long valorEnPesos,
                String referencia) {
        this.llaveIdempotencia = llaveIdempotencia;
        this.reservaId = reservaId;
        this.documento = documento;
        this.valorEnPesos = valorEnPesos;
        this.referencia = referencia;
        this.estado = EstadoPago.EN_PROCESO;
        this.fechaCreacion = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getLlaveIdempotencia() {
        return llaveIdempotencia;
    }

    public String getReservaId() {
        return reservaId;
    }

    public String getDocumento() {
        return documento;
    }

    public Long getValorEnPesos() {
        return valorEnPesos;
    }

    public String getReferencia() {
        return referencia;
    }

    public EstadoPago getEstado() {
        return estado;
    }

    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    public void cambiarEstado(EstadoPago nuevoEstado) {
        this.estado = nuevoEstado;
    }

    public boolean isCobroIniciado() {
        return cobroIniciado;
    }

    public void marcarCobroIniciado() {
        this.cobroIniciado = true;
    }
}
