# ADR-001: Arquitectura de Alta Disponibilidad y Escalabilidad Elástica para Picos de Venta en Temporada Alta

| Campo | Valor |
| :--- | :--- |
| **Estado** | Aceptado |
| **Fecha** | 16 de Octubre de 2026 |
| **Módulo** | `modulo-escalabilidad-alta-disponibilidad` |
| **Decisores** | Equipo de desarrollo (4 personas) |
| **ASRs relacionados** | **ASR 1**: Disponibilidad 99.95 % del 15 al 24 de diciembre (máximo 7 min de indisponibilidad), soporte de 16.000 tiquetes/día con $p95 < 3s$ en consultas y $p95 < 5s$ en compras, 0 % compras rechazadas por saturación, alertas automáticas en < 1 min y recuperación ante fallos en < 5 min. |
| **Relación con otros ADRs** | Decisión Raíz. Desacopla la infraestructura web mediante balanceo de carga y workers asíncronos. Complementa al ADR-002 (Caché en Redis de lecturas), ADR-003 (Reserva única de sillas) y ADR-004 (Idempotencia y pagos). |

---

## 1. Contexto

Durante el periodo de vacaciones del 15 al 24 de diciembre, la cooperativa de transporte experimenta un incremento de demanda de 20x (de 800 tiquetes diarios a más de 16.000), con concentración de hasta 6.400 compras en las 4 horas de mayor movimiento. El año anterior la plataforma colapsó por saturación y un único punto de falla, ocasionando pérdidas de 40 millones de pesos. 

Dado que el equipo de desarrollo está compuesto por solo 4 personas, no es viable mantener infraestructuras hipercomplejas de Kubernetes autogestionadas ni correr el riesgo de servidores únicos (VPS) no redundantes.

---

## 2. Decisión de Arquitectura

Se implementa una arquitectura redundante multi-instancia en contenedores administrados coordinados por un **Balanceador de Carga Automático (Nginx)** con distribución equitativa de tráfico (`least_conn` / `round-robin`), detección de salud activa (*healthchecks*) y tolerancia a fallos inmediata (*failover*).

Para el flujo de compras críticas durante el pico:
1. **Entrada de Tráfico:** El balanceador recibe el tráfico de la app móvil y lo reparte entre múltiples réplicas idénticas de la API (`transporte_api_node_1`, `transporte_api_node_2`, etc.).
2. **Desacoplamiento Asíncrono de Órdenes:** Las peticiones de compra no bloquean hilos síncronos de la base de datos; se ingresan a una cola de procesamiento en memoria/asíncrona garantizando tiempos de respuesta inmediatos ($p95 < 1s$) y cero rechazos por saturación.
3. **Workers Asíncronos Desacoplados:** Workers concurrentes procesan las compras en orden, invocan la pasarela de pagos con políticas de reintento exponencial y emiten el tiquete digital.
4. **Monitoreo y Alertas en Tiempo Real (< 1 min):** Un subsistema de métricas supervisa la tasa de error, el estado de cada réplica y la latencia del percentil 95. Si una réplica falla o la latencia supera el umbral, se dispara una alerta automática en menos de 1 minuto y el balanceador desvía el tráfico sin impacto al usuario.

---

## 3. Consecuencias

### Positivas
- **Alta Disponibilidad 99.95%:** Si un nodo de la aplicación cae, los demás nodos asumen el 100% de la carga sin caída de servicio para los pasajeros.
- **Resiliencia ante Picos:** La cola de compra amortigua ráfagas de 6.400 compras en 4 horas evitando caídas por saturación del pool de conexiones.
- **Baja Sobrecarga Operativa:** Configuración estandarizada basada en contenedores y balanceador declarativo adecuada para un equipo de 4 ingenieros.

### Mitigaciones
- **Saturación en el balanceador:** Configurado con límites de buffers, keepalives y compresión gzip.
- **Caída de réplica durante una transacción:** El balanceador reintenta la petición en el siguiente nodo sano vía `proxy_next_upstream`.
