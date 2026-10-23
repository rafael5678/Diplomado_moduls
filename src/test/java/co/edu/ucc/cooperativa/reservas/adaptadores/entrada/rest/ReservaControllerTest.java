package co.edu.ucc.cooperativa.reservas.adaptadores.entrada.rest;

import co.edu.ucc.cooperativa.reservas.dominio.modelo.EstadoReserva;
import co.edu.ucc.cooperativa.reservas.dominio.modelo.Reserva;
import co.edu.ucc.cooperativa.reservas.dominio.modelo.SillaNoDisponibleException;
import co.edu.ucc.cooperativa.reservas.dominio.puertos.ApartarSillaUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = ReservaController.class)
@ContextConfiguration(classes = ReservaControllerTest.TestWebConfig.class)
@DisplayName("Pruebas de Integración Ligeras: ReservaController Cooperativa (@WebMvcTest)")
class ReservaControllerTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({ReservaController.class, ReservaExceptionHandler.class})
    static class TestWebConfig {
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ApartarSillaUseCase apartarSillaUseCase;

    @Test
    @DisplayName("POST /api/v1/reservas debe retornar 201 CREATED con la reserva ACTIVA (ADR-003)")
    void debeApartarSillaExitosamente() throws Exception {
        LocalDateTime ahora = LocalDateTime.of(2026, 10, 23, 14, 0, 0);
        Reserva reservaMock = new Reserva(
                "RES-001",
                "VJ-COOP-01",
                "S-15",
                "PASAJERO-777",
                EstadoReserva.ACTIVA,
                ahora,
                ahora.plusMinutes(10),
                null
        );

        when(apartarSillaUseCase.apartarSilla(eq("VJ-COOP-01"), eq("S-15"), eq("PASAJERO-777")))
                .thenReturn(reservaMock);

        String json = """
                {
                    "viajeId": "VJ-COOP-01",
                    "sillaId": "S-15",
                    "pasajeroId": "PASAJERO-777"
                }
                """;

        mockMvc.perform(post("/api/v1/reservas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("RES-001"))
                .andExpect(jsonPath("$.viajeId").value("VJ-COOP-01"))
                .andExpect(jsonPath("$.sillaId").value("S-15"))
                .andExpect(jsonPath("$.pasajeroId").value("PASAJERO-777"))
                .andExpect(jsonPath("$.estado").value("ACTIVA"));
    }

    @Test
    @DisplayName("POST /api/v1/reservas debe retornar 409 CONFLICT cuando la silla ya está apartada")
    void debeRetornar409CuandoSillaYaEstaApartada() throws Exception {
        when(apartarSillaUseCase.apartarSilla(eq("VJ-COOP-01"), eq("S-15"), eq("PASAJERO-888")))
                .thenThrow(new SillaNoDisponibleException("La silla 'S-15' ya se encuentra apartada"));

        String json = """
                {
                    "viajeId": "VJ-COOP-01",
                    "sillaId": "S-15",
                    "pasajeroId": "PASAJERO-888"
                }
                """;

        mockMvc.perform(post("/api/v1/reservas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("SILLA_NO_DISPONIBLE"))
                .andExpect(jsonPath("$.mensaje").value("La silla 'S-15' ya se encuentra apartada"));
    }

    @Test
    @DisplayName("POST /api/v1/reservas/{id}/confirmar debe retornar 200 OK con estado CONFIRMADA")
    void debeConfirmarReserva() throws Exception {
        LocalDateTime ahora = LocalDateTime.of(2026, 10, 23, 14, 5, 0);
        Reserva confirmada = new Reserva(
                "RES-001",
                "VJ-COOP-01",
                "S-15",
                "PASAJERO-777",
                EstadoReserva.CONFIRMADA,
                ahora.minusMinutes(5),
                ahora.plusMinutes(5),
                ahora
        );

        when(apartarSillaUseCase.confirmarReserva("RES-001")).thenReturn(confirmada);

        mockMvc.perform(post("/api/v1/reservas/RES-001/confirmar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("RES-001"))
                .andExpect(jsonPath("$.estado").value("CONFIRMADA"));
    }

    @Test
    @DisplayName("GET /api/v1/reservas/{id} debe retornar 200 OK cuando la reserva existe")
    void debeConsultarReservaPorId() throws Exception {
        LocalDateTime ahora = LocalDateTime.of(2026, 10, 23, 14, 0, 0);
        Reserva reserva = new Reserva(
                "RES-001",
                "VJ-COOP-01",
                "S-15",
                "PASAJERO-777",
                EstadoReserva.ACTIVA,
                ahora,
                ahora.plusMinutes(10),
                null
        );

        when(apartarSillaUseCase.consultarReserva("RES-001")).thenReturn(Optional.of(reserva));

        mockMvc.perform(get("/api/v1/reservas/RES-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("RES-001"))
                .andExpect(jsonPath("$.sillaId").value("S-15"));
    }

    @Test
    @DisplayName("GET /api/v1/reservas/{id} debe retornar 404 NOT FOUND cuando la reserva no existe")
    void debeRetornar404CuandoNoExiste() throws Exception {
        when(apartarSillaUseCase.consultarReserva("RES-INEXISTENTE")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/reservas/RES-INEXISTENTE"))
                .andExpect(status().isNotFound());
    }
}
