package co.edu.ucc.cooperativa.reservas.infraestructura.config;

import co.edu.ucc.cooperativa.reservas.aplicacion.puertos.entrada.ApartarSillaUseCase;
import co.edu.ucc.cooperativa.reservas.aplicacion.puertos.salida.ReservaRepositoryPort;
import co.edu.ucc.cooperativa.reservas.aplicacion.puertos.salida.SillaCatalogoPort;
import co.edu.ucc.cooperativa.reservas.aplicacion.servicios.ApartarSillaService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Configuración de Spring para la inyección de dependencias de la Cooperativa.
 * Registra ApartarSillaService como un Bean administrado por Spring,
 * manteniendo el Dominio 100% puro y libre de @Service.
 */
@Configuration("cooperativaBeanConfiguration")
public class BeanConfiguration {

    @Bean("cooperativaApartarSillaUseCase")
    public ApartarSillaUseCase apartarSillaUseCase(
            @Qualifier("cooperativaReservaMongoAdapter") ReservaRepositoryPort reservaRepositoryPort,
            SillaCatalogoPort sillaCatalogoPort
    ) {
        return new ApartarSillaService(reservaRepositoryPort, sillaCatalogoPort, Clock.systemDefaultZone());
    }
}
