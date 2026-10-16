# Módulo de reserva de sillas (ADR-004)

Implementa el ADR "Cada silla se aparta y se vende una sola vez". Cubre el **ASR 2: 0 sillas vendidas dos veces en el mismo viaje**.

## Cómo funciona

1. El pasajero ve el mapa de sillas desde la caché (módulo de caché).
2. Toca una silla. La silla queda **apartada a su nombre por 10 minutos** mientras paga.
3. Si otro pasajero toca la misma silla en ese tiempo, recibe `409` con el mensaje "La silla ya está ocupada".
4. Si el pago se aprueba, el módulo de Pagos confirma la reserva y la silla queda **vendida**.
5. Si no paga en 10 minutos, un proceso que corre cada minuto **libera la silla**.

Quien decide si una silla está libre es la **base de datos**, no la caché. La tabla `reservas_silla` tiene la columna `llave_activa` (`viajeId:numeroSilla`) con restricción `UNIQUE`. Si dos servidores intentan apartar la misma silla en el mismo segundo, solo una inserción pasa.

```
APARTADA ──(Pagos confirma antes de 10 min)──► VENDIDA
    │
    └──(cancela o vencen los 10 min)──► LIBERADA   (la silla vuelve a DISPONIBLE)
```

## Unión con el módulo de caché

Cada vez que una silla se aparta, se vende o se libera, el servicio publica `SillaCambioEstadoEvent`. Después del commit, `InvalidacionCacheListener` llama a `CacheSillasService.evictMapaSillas(viajeId, motivo)` para borrar el mapa de ese viaje en Redis. El siguiente pasajero ve el mapa actualizado.

Si Redis está caído, la reserva igual queda guardada; el mapa se refresca cuando vence su TTL.

## API

| Método | Ruta | Qué hace | Respuestas |
| --- | --- | --- | --- |
| POST | `/api/v1/reservas` | Aparta una silla por 10 minutos | 201, 404, 409 |
| GET | `/api/v1/reservas/{id}` | Consulta la reserva y los segundos que le quedan | 200, 404 |
| POST | `/api/v1/reservas/{id}/confirmar` | Lo llama Pagos al aprobar el cobro. Repetirlo no cambia nada | 200, 404, 409 |
| DELETE | `/api/v1/reservas/{id}` | Cancela la reserva antes de pagar | 200, 404, 409 |

### Ejemplos

```bash
# 1. Apartar la silla 10 del viaje 1
curl -X POST http://localhost:8080/api/v1/reservas \
  -H "Content-Type: application/json" \
  -d '{"viajeId": 1, "numeroSilla": 10, "pasajeroId": "1085123456"}'

# 2. Otro pasajero intenta la misma silla -> 409 SILLA_NO_DISPONIBLE
curl -X POST http://localhost:8080/api/v1/reservas \
  -H "Content-Type: application/json" \
  -d '{"viajeId": 1, "numeroSilla": 10, "pasajeroId": "1085999999"}'

# 3. Pagos confirma la venta (usar el reservaId de la respuesta 1)
curl -X POST http://localhost:8080/api/v1/reservas/1/confirmar

# 4. El mapa ya muestra la silla 10 como OCUPADA (X-Cache: MISS porque se borró la caché)
curl -i http://localhost:8080/api/v1/viajes/1/sillas
```

### Prueba de concurrencia rápida

Lanza 20 solicitudes a la vez por la misma silla. Debe salir **un solo 201** y 19 respuestas 409:

```bash
for i in $(seq 1 20); do
  curl -s -o /dev/null -w "%{http_code}\n" -X POST http://localhost:8080/api/v1/reservas \
    -H "Content-Type: application/json" \
    -d "{\"viajeId\": 1, \"numeroSilla\": 11, \"pasajeroId\": \"pasajero-$i\"}" &
done; wait
```

## Configuración

| Propiedad | Variable de entorno | Valor por defecto |
| --- | --- | --- |
| `reservas.tiempo-reserva-minutos` | `RESERVAS_TIEMPO_MINUTOS` | 10 |
| `reservas.liberacion.intervalo-ms` | `RESERVAS_LIBERACION_INTERVALO_MS` | 60000 (1 minuto) |
| `reservas.alerta.atraso-minutos` | `RESERVAS_ALERTA_ATRASO_MINUTOS` | 15 |

## Archivos del módulo

```
src/main/java/com/example/modulocachelecturas/Modulo-reserva-sillas/
├── controller/  ReservaSillaController, ReservaDTOs, ReservaExceptionHandler
├── model/       ReservaSilla, EstadoReserva
├── repository/  ReservaSillaRepository, SillaReservaRepository
└── service/     ReservaSillaService, LiberadorReservasVencidas,
                 InvalidacionCacheListener, SillaCambioEstadoEvent,
                 ReservaExceptions, ReservasConfig
```

Pruebas: `ReservaSillaServiceTest` y `InvalidacionCacheListenerTest` (`./mvnw test`).
