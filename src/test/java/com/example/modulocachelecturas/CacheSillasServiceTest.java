package com.example.modulocachelecturas;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CacheSillasServiceTest {

    @Mock
    private CacheRepository cacheRepository;

    @Mock
    private ViajeRepository viajeRepository;

    @Mock
    private SillaRepository sillaRepository;

    @InjectMocks
    private CacheSillasService cacheSillasService;

    private Long viajeId;
    private Viaje viajeMock;
    private MapaSillasDTO cachedDtoMock;

    @BeforeEach
    void setUp() {
        viajeId = 1L;

        ReflectionTestUtils.setField(cacheSillasService, "sillasTtlSeconds", 240L);
        ReflectionTestUtils.setField(cacheSillasService, "viajesTtlSeconds", 300L);
        ReflectionTestUtils.setField(cacheSillasService, "lockTimeoutMillis", 5000L);
        ReflectionTestUtils.setField(cacheSillasService, "lockRetryIntervalMillis", 10L);
        ReflectionTestUtils.setField(cacheSillasService, "lockMaxRetries", 5);

        Ruta ruta = Ruta.builder()
                .id(10L)
                .codigoRuta("BOG-MED")
                .origen("Bogotá")
                .destino("Medellín")
                .distanciaKm(420.0)
                .duracionEstimadaHoras(8.5)
                .build();

        viajeMock = Viaje.builder()
                .id(viajeId)
                .codigoViaje("VJ-101")
                .ruta(ruta)
                .fechaSalida(LocalDate.of(2026, 12, 1))
                .horaSalida(LocalTime.of(8, 0))
                .busPlaca("XYZ-789")
                .capacidadTotal(2)
                .precioBase(new BigDecimal("80000.00"))
                .estado("PROGRAMADO")
                .sillas(new ArrayList<>())
                .build();

        Silla silla1 = Silla.builder()
                .id(101L)
                .viaje(viajeMock)
                .numeroSilla(1)
                .piso(1)
                .ubicacion("VENTANA")
                .estado(EstadoSilla.DISPONIBLE)
                .precio(new BigDecimal("80000.00"))
                .build();

        Silla silla2 = Silla.builder()
                .id(102L)
                .viaje(viajeMock)
                .numeroSilla(2)
                .piso(1)
                .ubicacion("PASILLO")
                .estado(EstadoSilla.OCUPADA)
                .precio(new BigDecimal("80000.00"))
                .build();

        viajeMock.getSillas().add(silla1);
        viajeMock.getSillas().add(silla2);

        cachedDtoMock = MapaSillasDTO.builder()
                .viajeId(viajeId)
                .codigoViaje("VJ-101")
                .origen("Bogotá")
                .destino("Medellín")
                .totalSillas(2)
                .sillasDisponibles(1)
                .sillasOcupadas(1)
                .source("CACHE_REDIS (Hit)")
                .build();
    }

    @Test
    @DisplayName("ASR 1 - Cache Hit: Debe retornar mapa desde Redis en milisegundos sin tocar PostgreSQL")
    void testCacheHit_ReturnsFromRedisWithoutQueryingDatabase() {
        String cacheKey = "viaje:1:mapa_sillas";
        when(cacheRepository.get(eq(cacheKey), eq(MapaSillasDTO.class))).thenReturn(Optional.of(cachedDtoMock));

        MapaSillasDTO result = cacheSillasService.getMapaSillas(viajeId);

        assertNotNull(result);
        assertEquals("VJ-101", result.getCodigoViaje());
        assertTrue(result.getSource().contains("CACHE_REDIS"));

        // Verificación crítica: La base de datos relacional NO debe haber sido consultada
        verifyNoInteractions(viajeRepository);
        verify(cacheRepository, times(1)).get(cacheKey, MapaSillasDTO.class);
    }

    @Test
    @DisplayName("Cache Miss: Debe adquirir Mutex Lock, consultar PostgreSQL, guardar en Redis con TTL 240s y liberar Lock")
    void testCacheMiss_QueriesDatabaseAndPopulatesRedis() {
        String cacheKey = "viaje:1:mapa_sillas";
        String lockKey = "lock:viaje:1:mapa_sillas";

        when(cacheRepository.get(eq(cacheKey), eq(MapaSillasDTO.class)))
                .thenReturn(Optional.empty()); // Primer intento: Miss
        when(cacheRepository.acquireLock(eq(lockKey), anyString(), eq(5000L))).thenReturn(true);
        when(viajeRepository.findByIdWithSillas(viajeId)).thenReturn(Optional.of(viajeMock));
        when(cacheRepository.releaseLock(eq(lockKey), anyString())).thenReturn(true);

        MapaSillasDTO result = cacheSillasService.getMapaSillas(viajeId);

        assertNotNull(result);
        assertEquals("VJ-101", result.getCodigoViaje());
        assertEquals("DATABASE_POSTGRES (Miss)", result.getSource());
        assertEquals(2, result.getTotalSillas());
        assertEquals(1, result.getSillasDisponibles());
        assertEquals(1, result.getSillasOcupadas());

        // Verificaciones
        verify(cacheRepository).acquireLock(eq(lockKey), anyString(), eq(5000L));
        verify(viajeRepository).findByIdWithSillas(viajeId);
        verify(cacheRepository).set(eq(cacheKey), any(MapaSillasDTO.class), eq(240L));
        verify(cacheRepository).releaseLock(eq(lockKey), anyString());
    }

    @Test
    @DisplayName("Invalidación por Evento: Debe eliminar inmediatamente la clave en Redis al confirmar reserva")
    void testInvalidateCache_DeletesRedisKey() {
        String cacheKey = "viaje:1:mapa_sillas";
        when(cacheRepository.delete(cacheKey)).thenReturn(true);

        boolean invalidated = cacheSillasService.evictMapaSillas(viajeId, "RESERVA_CONFIRMADA");

        assertTrue(invalidated);
        verify(cacheRepository, times(1)).delete(cacheKey);
        verify(cacheRepository, times(1)).deleteByPattern("viajes:disponibles:*");
    }

    @Test
    @DisplayName("Anti-Cache Stampede: Hilos concurrentes esperan y consumen de Redis cuando otro hilo adquiere el Lock")
    void testMutexLock_Contention_SecondThreadWaitsAndGetsCacheHit() {
        String cacheKey = "viaje:1:mapa_sillas";
        String lockKey = "lock:viaje:1:mapa_sillas";

        // Primer intento de lectura: Miss
        // Intento de adquirir lock: Fails (otro hilo lo tiene)
        when(cacheRepository.get(eq(cacheKey), eq(MapaSillasDTO.class)))
                .thenReturn(Optional.empty()) // Chequeo inicial
                .thenReturn(Optional.of(cachedDtoMock)); // En el reintento de espera, ya está disponible en Redis

        when(cacheRepository.acquireLock(eq(lockKey), anyString(), eq(5000L))).thenReturn(false);

        MapaSillasDTO result = cacheSillasService.getMapaSillas(viajeId);

        assertNotNull(result);
        assertTrue(result.getSource().contains("CACHE_REDIS"));

        // No debe tocar la base de datos ya que se recuperó desde la caché tras la espera
        verifyNoInteractions(viajeRepository);
    }
}
