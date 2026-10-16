package com.example.modulocachelecturas.escalabilidad.model;

import java.time.LocalDateTime;

public class MetricasDisponibilidadDTO {

    private String instanceId;
    private double disponibilidadPorcentaje;
    private long totalPeticionesAtendidas;
    private long totalErrores5xx;
    private double latenciaPromedioMs;
    private double latenciaP95Ms;
    private boolean alertaActiva;
    private String mensajeAlerta;
    private LocalDateTime timestamp;

    public MetricasDisponibilidadDTO() {
    }

    public MetricasDisponibilidadDTO(String instanceId, double disponibilidadPorcentaje,
                                   long totalPeticionesAtendidas, long totalErrores5xx,
                                   double latenciaPromedioMs, double latenciaP95Ms,
                                   boolean alertaActiva, String mensajeAlerta,
                                   LocalDateTime timestamp) {
        this.instanceId = instanceId;
        this.disponibilidadPorcentaje = disponibilidadPorcentaje;
        this.totalPeticionesAtendidas = totalPeticionesAtendidas;
        this.totalErrores5xx = totalErrores5xx;
        this.latenciaPromedioMs = latenciaPromedioMs;
        this.latenciaP95Ms = latenciaP95Ms;
        this.alertaActiva = alertaActiva;
        this.mensajeAlerta = mensajeAlerta;
        this.timestamp = timestamp;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public double getDisponibilidadPorcentaje() {
        return disponibilidadPorcentaje;
    }

    public void setDisponibilidadPorcentaje(double disponibilidadPorcentaje) {
        this.disponibilidadPorcentaje = disponibilidadPorcentaje;
    }

    public long getTotalPeticionesAtendidas() {
        return totalPeticionesAtendidas;
    }

    public void setTotalPeticionesAtendidas(long totalPeticionesAtendidas) {
        this.totalPeticionesAtendidas = totalPeticionesAtendidas;
    }

    public long getTotalErrores5xx() {
        return totalErrores5xx;
    }

    public void setTotalErrores5xx(long totalErrores5xx) {
        this.totalErrores5xx = totalErrores5xx;
    }

    public double getLatenciaPromedioMs() {
        return latenciaPromedioMs;
    }

    public void setLatenciaPromedioMs(double latenciaPromedioMs) {
        this.latenciaPromedioMs = latenciaPromedioMs;
    }

    public double getLatenciaP95Ms() {
        return latenciaP95Ms;
    }

    public void setLatenciaP95Ms(double latenciaP95Ms) {
        this.latenciaP95Ms = latenciaP95Ms;
    }

    public boolean isAlertaActiva() {
        return alertaActiva;
    }

    public void setAlertaActiva(boolean alertaActiva) {
        this.alertaActiva = alertaActiva;
    }

    public String getMensajeAlerta() {
        return mensajeAlerta;
    }

    public void setMensajeAlerta(String mensajeAlerta) {
        this.mensajeAlerta = mensajeAlerta;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
}
