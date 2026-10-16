package com.example.modulocachelecturas.reservas.service;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

/**
 * CONFIGURACIÓN DEL MÓDULO DE RESERVAS.
 *
 * - @EnableScheduling activa el proceso que libera las reservas vencidas.
 * - El reloj (Clock) se inyecta como bean para que las pruebas puedan fijar la hora
 *   y verificar el vencimiento de los 10 minutos sin esperar.
 */
@Configuration
@EnableScheduling
public class ReservasConfig {

    @Bean
    public Clock relojReservas() {
        return Clock.systemDefaultZone();
    }
}
