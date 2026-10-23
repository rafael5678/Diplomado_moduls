package co.edu.ucc.cooperativa.reservas.infraestructura.config;

import co.edu.ucc.cooperativa.reservas.aplicacion.puertos.entrada.ApartarSillaUseCase;
import co.edu.ucc.cooperativa.reservas.aplicacion.puertos.salida.ReservaRepositoryPort;
import co.edu.ucc.cooperativa.reservas.aplicacion.puertos.salida.SillaCatalogoPort;
import co.edu.ucc.cooperativa.reservas.aplicacion.servicios.ApartarSillaService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.time.Clock;

/**
 * Configuración de Spring para la integración del adaptador MySQL sin alterar Mongo ni otros módulos.
 */
@Configuration("cooperativaMySqlBeanConfiguration")
@Profile("mysql")
public class MySqlBeanConfiguration {

    @Bean("cooperativaApartarSillaUseCaseMySql")
    public ApartarSillaUseCase apartarSillaUseCaseMySql(
            @Qualifier("cooperativaReservaMySqlAdapter") ReservaRepositoryPort reservaRepositoryPort,
            SillaCatalogoPort sillaCatalogoPort
    ) {
        return new ApartarSillaService(reservaRepositoryPort, sillaCatalogoPort, Clock.systemDefaultZone());
    }
}
