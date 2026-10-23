package co.edu.ucc.cooperativa.config;

import co.edu.ucc.cooperativa.reservas.dominio.puertos.ApartarSillaUseCase;
import co.edu.ucc.cooperativa.reservas.dominio.puertos.ReservaRepositoryPort;
import co.edu.ucc.cooperativa.reservas.dominio.servicio.ApartarSillaService;
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
            @Qualifier("cooperativaReservaMongoAdapter") ReservaRepositoryPort reservaRepositoryPort
    ) {
        return new ApartarSillaService(reservaRepositoryPort, Clock.systemDefaultZone());
    }
}
