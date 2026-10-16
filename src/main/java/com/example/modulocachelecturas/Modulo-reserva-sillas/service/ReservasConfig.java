package com.example.modulocachelecturas.reservas.service;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * CONFIGURACIÓN DEL MÓDULO DE RESERVAS.
 *
 * El reloj (Clock) se inyecta como bean para que las pruebas puedan fijar la hora
 * y verificar el vencimiento de los 10 minutos sin esperar.
 */
@Configuration
public class ReservasConfig {

    @Bean
    public Clock relojReservas() {
        return Clock.systemDefaultZone();
    }
}
