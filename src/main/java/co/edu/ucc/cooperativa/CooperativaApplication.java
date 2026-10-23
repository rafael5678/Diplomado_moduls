package co.edu.ucc.cooperativa;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Clase principal de inicio para la aplicación de Cooperativa de Transporte.
 * Módulo de Reservas con Arquitectura Hexagonal y persistencia NoSQL en MongoDB.
 */
@SpringBootApplication
public class CooperativaApplication {

    public static void main(String[] args) {
        SpringApplication.run(CooperativaApplication.class, args);
    }
}
