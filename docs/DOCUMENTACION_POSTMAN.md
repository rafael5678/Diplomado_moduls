# Documentación Técnica: Colección de Pruebas Postman
## Módulo ADR-002: Caché Distribuida y Descarga de Lecturas
**Proyecto:** Sistema de Cooperativa de Transporte  
**Archivo:** `Modulo_Cache_Lecturas.postman_collection.json`  
**Estándar:** Postman Collection Format v2.1.0  

---

## 1. ¿Qué es este archivo?
Es un archivo de intercambio en formato **JSON** estándar de Postman que contiene el conjunto de pruebas automatizadas y manuales para validar el **Módulo de Caché Distribuida y Descarga de Lecturas**. 

Permite a cualquier desarrollador, evaluador o arquitecto de software importar todas las rutas de la API en Postman con un solo clic, sin tener que configurar manualmente URLs, métodos HTTP, cabeceras (`Headers`) ni cuerpos de datos (`Body`).

---

## 2. Estructura General del JSON

```json
{
  "info": { ... },    // Metadatos de la colección (Nombre, ID, Versión del Esquema)
  "item": [ ... ]     // Listado de las 5 peticiones HTTP preconfiguradas
}
```

* **`_postman_id`**: Identificador único global de la colección dentro de Postman.
* **`name`**: Título legible que aparece en el panel lateral de Postman (`Módulo ADR-002: Caché Distribuida y Lecturas`).
* **`description`**: Resumen funcional del módulo y su propósito arquitectónico.
* **`schema`**: Especificación oficial de Postman (`collection/v2.1.0/collection.json`) que garantiza compatibilidad universal en cualquier versión de Postman.

---

## 3. Desglose Detallado de las 5 Peticiones

### Petición 1: Consultar Mapa de Sillas (Demostración de Hit / Miss)
* **Nombre:** `1. Consultar Mapa de Sillas (Hit / Miss)`
* **Método HTTP:** `GET`
* **URL:** `http://localhost:8080/api/v1/viajes/1/sillas`
* **Cabeceras:** Ninguna requerida (petición de lectura pública).
* **Cuerpo:** Vacío.
* **Objetivo de Arquitectura (ASR 1):**
  Demuestra en vivo el patrón **Cache-Aside**:
  1. **Primera ejecución (Cache Miss):** Redis no tiene los datos en RAM. El microservicio viaja a PostgreSQL, ejecuta el `JOIN FETCH` de las 40 sillas, guarda una copia serializada en Redis con un TTL de 240 segundos y retorna.
     * Cabecera recibida: `X-Cache: MISS`
     * Tiempo de respuesta: ~200 - 900 ms (tiempo de conexión a base de datos).
  2. **Segunda ejecución (Cache Hit):** El servicio detecta los datos en memoria RAM de Redis y responde inmediatamente sin tocar la base de datos PostgreSQL.
     * Cabecera recibida: `X-Cache: HIT`
     * Tiempo de respuesta: **< 15 ms** (cumpliendo el ASR 1 de latencia mínima).

---

### Petición 2: Consultar Viajes Disponibles
* **Nombre:** `2. Consultar Viajes Disponibles`
* **Método HTTP:** `GET`
* **URL:** `http://localhost:8080/api/v1/viajes/disponibles?origen=Bogota&destino=Medellin`
* **Query Parameters:**
  * `origen`: `Bogota`
  * `destino`: `Medellin`
* **Cuerpo:** Vacío.
* **Objetivo:**
  Permite validar la búsqueda rápida de itinerarios filtrados por ruta. El resultado se mantiene en caché durante 300 segundos (5 minutos) para absorber las consultas recurrentes de pasajeros sin saturar el motor relacional.

---

### Petición 3: Invalidar Caché por Evento
* **Nombre:** `3. Invalidar Caché por Evento`
* **Método HTTP:** `POST`
* **URL:** `http://localhost:8080/api/v1/cache/invalidate`
* **Cabeceras:**
  * `Content-Type: application/json`
* **Cuerpo (JSON Raw):**
  ```json
  {
    "viajeId": 1,
    "motivo": "RESERVA_CONFIRMADA"
  }
  ```
* **Objetivo de Arquitectura (Consistencia Eventual):**
  Simula la integración desacoplada con los módulos de **Reservas** o **Pagos**. Cuando un usuario compra o reserva una silla, el otro módulo envía este evento para expulsar inmediatamente (`DEL`) la clave del viaje de Redis. Esto garantiza que ningún otro usuario vea la silla como disponible por error.
* **Respuesta esperada:**
  ```json
  {
    "status": "SUCCESS",
    "viajeId": 1,
    "cacheCleaned": true
  }
  ```

---

### Petición 4: Auditar Métricas ASR 1 (Hit Ratio y Observabilidad)
* **Nombre:** `4. Auditar Métricas ASR 1 (Hit Ratio)`
* **Método HTTP:** `GET`
* **URL:** `http://localhost:8080/api/v1/cache/metrics`
* **Cuerpo:** Vacío.
* **Objetivo de Arquitectura:**
  Provee telemetría en tiempo real para verificar el cumplimiento del requerimiento de calidad **ASR 1 (Rendimiento)**:
  * `totalRequests`: Total de lecturas atendidas.
  * `cacheHits`: Lecturas servidas directamente desde memoria RAM (Redis).
  * `cacheMisses`: Lecturas que requirieron consulta a base de datos.
  * `hitRatioPercentage`: Porcentaje de efectividad de la memoria caché.
  * `dbReadsSavedPercentage`: Porcentaje exacto de carga ahorrada a PostgreSQL.
  * `asr1PerformanceTargetMet`: Indicador booleano que se torna en `true` cuando el ahorro de lectura supera la meta del **85%**.

---

### Petición 5: Precalentamiento de Caché (Warmup Proactivo)
* **Nombre:** `5. Precalentamiento de Caché (Warmup)`
* **Método HTTP:** `POST`
* **URL:** `http://localhost:8080/api/v1/cache/warmup/1`
* **Cuerpo:** Vacío.
* **Objetivo de Arquitectura (Resiliencia y Anti-Stampede):**
  Evita el problema del arranque en frío (*Cold Start*) y avalancha (*Cache Stampede*). Permite a los administradores o procesos programados cargar el viaje completo en memoria Redis antes del inicio de una venta masiva o temporada alta, asegurando que el 100% de los usuarios reciban un `HIT` instantáneo desde el primer segundo.

---

## 4. Guía para una Demostración o Sustentación en Vivo

Para explicar este archivo ante el docente o tu equipo, se recomienda seguir esta secuencia:

1. **Paso 1 (Lanzar Petición 1):** Hacer clic en `Send`. Mostrar que en *Headers* dice `X-Cache: MISS` y tardó varios cientos de milisegundos.
2. **Paso 2 (Volver a lanzar Petición 1):** Volver a hacer clic en `Send`. Mostrar cómo cambió a `X-Cache: HIT` y el tiempo cayó a menos de 15 ms.
3. **Paso 3 (Ver Métricas con Petición 4):** Mostrar que el `hitRatioPercentage` subió al 50%.
4. **Paso 4 (Invalidar con Petición 3):** Simular una compra disparando la invalidación.
5. **Paso 5 (Comprobar Consistencia):** Volver a lanzar la Petición 1 para demostrar que al invalidarse la caché, el sistema vuelve a ir a la BD de forma transparente (`MISS`) para traer el estado actualizado.
