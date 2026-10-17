package com.example.modulocachelecturas.escalabilidad.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class OrdenCompraDTO {

    private String idOrden;
    private Long viajeId;
    private Integer numeroSilla;
    private String pasajeroNombre;
    private String pasajeroEmail;
    private String pasajeroDocumento;
    private BigDecimal monto;
    private EstadoOrdenCompra estado;
    private String idTransaccionPasarela;
    private String codigoTiquete;
    private String nodoProcesador;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaProcesamiento;

    public OrdenCompraDTO() {
    }

    public OrdenCompraDTO(String idOrden, Long viajeId, Integer numeroSilla,
                          String pasajeroNombre, String pasajeroEmail, String pasajeroDocumento,
                          BigDecimal monto) {
        this.idOrden = idOrden;
        this.viajeId = viajeId;
        this.numeroSilla = numeroSilla;
        this.pasajeroNombre = pasajeroNombre;
        this.pasajeroEmail = pasajeroEmail;
        this.pasajeroDocumento = pasajeroDocumento;
        this.monto = monto;
        this.estado = EstadoOrdenCompra.EN_COLA;
        this.fechaCreacion = LocalDateTime.now();
    }

    public String getIdOrden() {
        return idOrden;
    }

    public void setIdOrden(String idOrden) {
        this.idOrden = idOrden;
    }

    public Long getViajeId() {
        return viajeId;
    }

    public void setViajeId(Long viajeId) {
        this.viajeId = viajeId;
    }

    public Integer getNumeroSilla() {
        return numeroSilla;
    }

    public void setNumeroSilla(Integer numeroSilla) {
        this.numeroSilla = numeroSilla;
    }

    public String getPasajeroNombre() {
        return pasajeroNombre;
    }

    public void setPasajeroNombre(String pasajeroNombre) {
        this.pasajeroNombre = pasajeroNombre;
    }

    public String getPasajeroEmail() {
        return pasajeroEmail;
    }

    public void setPasajeroEmail(String pasajeroEmail) {
        this.pasajeroEmail = pasajeroEmail;
    }

    public String getPasajeroDocumento() {
        return pasajeroDocumento;
    }

    public void setPasajeroDocumento(String pasajeroDocumento) {
        this.pasajeroDocumento = pasajeroDocumento;
    }

    public BigDecimal getMonto() {
        return monto;
    }

    public void setMonto(BigDecimal monto) {
        this.monto = monto;
    }

    public EstadoOrdenCompra getEstado() {
        return estado;
    }

    public void setEstado(EstadoOrdenCompra estado) {
        this.estado = estado;
    }

    public String getIdTransaccionPasarela() {
        return idTransaccionPasarela;
    }

    public void setIdTransaccionPasarela(String idTransaccionPasarela) {
        this.idTransaccionPasarela = idTransaccionPasarela;
    }

    public String getCodigoTiquete() {
        return codigoTiquete;
    }

    public void setCodigoTiquete(String codigoTiquete) {
        this.codigoTiquete = codigoTiquete;
    }

    public String getNodoProcesador() {
        return nodoProcesador;
    }

    public void setNodoProcesador(String nodoProcesador) {
        this.nodoProcesador = nodoProcesador;
    }

    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    public void setFechaCreacion(LocalDateTime fechaCreacion) {
        this.fechaCreacion = fechaCreacion;
    }

    public LocalDateTime getFechaProcesamiento() {
        return fechaProcesamiento;
    }

    public void setFechaProcesamiento(LocalDateTime fechaProcesamiento) {
        this.fechaProcesamiento = fechaProcesamiento;
    }
}
