# Tasks: Módulo de Escalabilidad y Alta Disponibilidad (ASR-001 / ADR-001)

Rama: `modulo-escalabilidad-alta-disponibilidad`

- [x] 1. Definición y Diseño de la Solución ASR1 / ADR-001 <!-- id: 1 -->
  - [x] Análisis del documento Taller 1 (ASR1: Disponibilidad 99.95%, picos de 16.000 tiquetes/día, p95 < 3s, alertas < 1 min, recuperación < 5 min; ADR-001: Múltiples instancias en contenedores + balanceador de carga + cola asíncrona + monitoreo/salud). <!-- id: 1.1 -->
  - [x] Creación de la rama `modulo-escalabilidad-alta-disponibilidad`. <!-- id: 1.2 -->
  - [x] Elaborar especificación y documentación técnica del módulo (`docs/adr/0001-alta-disponibilidad-escalabilidad.md`). <!-- id: 1.3 -->
- [x] 2. Infraestructura de Alta Disponibilidad y Balanceo de Carga <!-- id: 2 -->
  - [x] Configuración del Balanceador de Carga (Reverse Proxy Nginx) con estrategia de distribución de carga equitativa y failover activo hacia réplicas. <!-- id: 2.1 -->
  - [x] Actualización de `docker-compose.yml` para soportar múltiples réplicas de la API (`api-node-1`, `api-node-2`, etc.), healthchecks automáticos y reinicio resiliente. <!-- id: 2.2 -->
  - [x] Configuración del Nginx con timeouts optimizados para p95 y página de fallback/mantenimiento elegante ante saturación extrema. <!-- id: 2.3 -->
- [x] 3. Endpoints de Salud, Métricas y Alertas en Tiempo Real (Cumplimiento ASR1) <!-- id: 3 -->
  - [x] Implementar dependencias de Spring Boot Actuator y Micrometer en `pom.xml`. <!-- id: 3.1 -->
  - [x] Crear servicio/controlador de monitoreo de disponibilidad y simulación de carga/salud (`HealthAlertController`, `DisponibilidadService`). <!-- id: 3.2 -->
  - [x] Implementar sistema de detección de latencia anómala y emisión de alertas automáticas (< 1 minuto) para el equipo de desarrollo. <!-- id: 3.3 -->
- [x] 4. Procesamiento Asíncrono y Cola de Órdenes Desacoplada (ADR-001 Punto 3 y 4) <!-- id: 4 -->
  - [x] Diseñar el modelo de Orden de Compra asíncrona (`CompraRequestDTO`, `CompraResponseDTO`, `EstadoOrdenCompra`). <!-- id: 4.1 -->
  - [x] Implementar cola de procesamiento asíncrona desacoplada con workers concurrentes para absorber ráfagas de 16.000 tiquetes sin saturar la base de datos ni bloquear el hilo HTTP. <!-- id: 4.2 -->
  - [x] Integrar con pasarela y simulación de emisión de tiquete digital tras confirmación de pago. <!-- id: 4.3 -->
- [x] 5. Pruebas Automatizadas y Verificación de Criterios ASR-001 <!-- id: 5 -->
  - [x] Pruebas unitarias de balanceo, salud y procesamiento asíncrono con JUnit y Mockito. <!-- id: 5.1 -->
  - [x] Prueba de estrés/simulación de concurrencia (script PowerShell/Postman) para verificar failover de nodos, p95 y cero rechazos por saturación. <!-- id: 5.2 -->
  - [x] Colección Postman de verificación y testing del módulo. <!-- id: 5.3 -->
- [x] 6. Finalización, Revisión y Commits <!-- id: 6 -->
  - [x] Verificación de compilación Maven (`mvn test` o `mvn clean compile`). <!-- id: 6.1 -->
  - [ ] Commits descriptivos y preparación de entrega. <!-- id: 6.2 -->
