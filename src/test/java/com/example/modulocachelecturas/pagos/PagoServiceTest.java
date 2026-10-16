package com.example.modulocachelecturas.pagos;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class PagoServiceTest {

    @Mock
    private PagoRepository repositorio;

    @Mock
    private RegistroPagoService registro;

    @Mock
    private PasarelaSimulada pasarela;

    private PagoService servicio;

    private Pago pago;

    @BeforeEach
    void configurar() {
        MockitoAnnotations.openMocks(this);

        servicio = new PagoService(
                repositorio,
                registro,
                pasarela
        );

        pago = new Pago(
                "llave-123",
                "reserva-001",
                "1085",
                240000L,
                "referencia-001"
        );
    }

    @Test
    void debeRechazarCobroSinLlave() {
        assertThrows(
                IllegalArgumentException.class,
                () -> servicio.procesarCobro(
                        "",
                        "reserva-001",
                        "1085",
                        240000L
                )
        );

        verifyNoInteractions(registro, pasarela);
    }

    @Test
    void debeDevolverPagoExistenteSinCobrarOtraVez() {
        when(repositorio.findByLlaveIdempotencia("llave-123"))
                .thenReturn(Optional.of(pago));

        Pago resultado = servicio.procesarCobro(
                "llave-123",
                "reserva-001",
                "1085",
                240000L
        );

        assertSame(pago, resultado);
        verifyNoInteractions(registro, pasarela);
    }

    @Test
    void debeRechazarMismaLlaveConValorDiferente() {
        when(repositorio.findByLlaveIdempotencia("llave-123"))
                .thenReturn(Optional.of(pago));

        assertThrows(
                ConflictoIdempotenciaException.class,
                () -> servicio.procesarCobro(
                        "llave-123",
                        "reserva-001",
                        "1085",
                        999000L
                )
        );

        verifyNoInteractions(registro, pasarela);
    }

    @Test
    void debeConsultarPagoPorReferencia() {
        when(repositorio.findByReferencia("referencia-001"))
                .thenReturn(Optional.of(pago));

        Pago resultado = servicio.buscarPorReferencia(
                "referencia-001"
        );

        assertSame(pago, resultado);
    }

    @Test
    void debeRechazarReferenciaInexistente() {
        when(repositorio.findByReferencia("no-existe"))
                .thenReturn(Optional.empty());

        assertThrows(
                PagoNoEncontradoException.class,
                () -> servicio.buscarPorReferencia("no-existe")
        );
    }

        @Test
        void debeProcesarCobroNuevoUnaSolaVez() {
        when(repositorio.findByLlaveIdempotencia("llave-123"))
                .thenReturn(Optional.empty());

        when(registro.registrar(
                "llave-123",
                "reserva-001",
                "1085",
                240000L
        )).thenReturn(pago);

        when(registro.intentarReclamarCobro(null))
                .thenReturn(true);

        when(pasarela.cobrar("referencia-001", 240000L))
                .thenReturn(EstadoPago.APROBADO);

        when(registro.actualizarEstado(null, EstadoPago.APROBADO))
                .thenAnswer(invocacion -> {
                        pago.cambiarEstado(EstadoPago.APROBADO);
                        return pago;
                });

        Pago resultado = servicio.procesarCobro(
                "llave-123",
                "reserva-001",
                "1085",
                240000L
        );

        assertEquals(EstadoPago.APROBADO, resultado.getEstado());

        verify(pasarela, times(1))
                .cobrar("referencia-001", 240000L);

        verify(registro, times(1))
                .actualizarEstado(null, EstadoPago.APROBADO);
        }

    @Test
    void noDebeCobrarSiOtroProcesoYaReclamoElPago() {
        when(registro.intentarReclamarCobro(null))
                .thenReturn(false);

        when(registro.consultarPago(null))
                .thenReturn(pago);

        Pago resultado = servicio.ejecutarCobro(pago);

        assertSame(pago, resultado);
        verifyNoInteractions(pasarela);
    }
}
