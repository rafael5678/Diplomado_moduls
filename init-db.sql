-- ============================================================================
-- SCRIPT DE INICIALIZACIÓN DE BASE DE DATOS (PostgreSQL)
-- Diplomado en Arquitectura de Software - Cooperativa de Transporte
-- ADR-002: Caché Distribuida y Descarga de Lecturas
-- ============================================================================

-- 1. Tabla de Rutas
CREATE TABLE IF NOT EXISTS rutas (
    id BIGSERIAL PRIMARY KEY,
    codigo_ruta VARCHAR(50) NOT NULL UNIQUE,
    origen VARCHAR(100) NOT NULL,
    destino VARCHAR(100) NOT NULL,
    distancia_km DOUBLE PRECISION NOT NULL,
    duracion_estimada_horas DOUBLE PRECISION NOT NULL
);

-- 2. Tabla de Viajes
CREATE TABLE IF NOT EXISTS viajes (
    id BIGSERIAL PRIMARY KEY,
    codigo_viaje VARCHAR(50) NOT NULL UNIQUE,
    ruta_id BIGINT NOT NULL REFERENCES rutas(id) ON DELETE CASCADE,
    fecha_salida DATE NOT NULL,
    hora_salida TIME NOT NULL,
    bus_placa VARCHAR(20) NOT NULL,
    capacidad_total INTEGER NOT NULL,
    precio_base NUMERIC(12, 2) NOT NULL,
    estado VARCHAR(30) NOT NULL DEFAULT 'PROGRAMADO'
);

-- 3. Tabla de Sillas
CREATE TABLE IF NOT EXISTS sillas (
    id BIGSERIAL PRIMARY KEY,
    viaje_id BIGINT NOT NULL REFERENCES viajes(id) ON DELETE CASCADE,
    numero_silla INTEGER NOT NULL,
    piso INTEGER NOT NULL,
    ubicacion VARCHAR(20) NOT NULL, -- 'VENTANA', 'PASILLO'
    estado VARCHAR(20) NOT NULL DEFAULT 'DISPONIBLE', -- 'DISPONIBLE', 'RESERVADA', 'OCUPADA'
    precio NUMERIC(12, 2) NOT NULL,
    CONSTRAINT uq_viaje_silla UNIQUE (viaje_id, numero_silla)
);

-- 4. Tabla de Reservas de Silla (ADR-004: cada silla se aparta y se vende una sola vez)
-- 'llave_activa' = 'viajeId:numeroSilla' mientras la reserva está APARTADA o VENDIDA.
-- Su restricción UNIQUE impide que dos pasajeros tengan la misma silla al mismo tiempo,
-- aunque las solicitudes lleguen desde servidores distintos en el mismo segundo.
-- Al liberar la reserva queda en NULL y la silla se puede volver a apartar.
CREATE TABLE IF NOT EXISTS reservas_silla (
    id BIGSERIAL PRIMARY KEY,
    viaje_id BIGINT NOT NULL REFERENCES viajes(id) ON DELETE CASCADE,
    numero_silla INTEGER NOT NULL,
    pasajero_id VARCHAR(60) NOT NULL,
    estado VARCHAR(20) NOT NULL, -- 'APARTADA', 'VENDIDA', 'LIBERADA'
    llave_activa VARCHAR(40),
    creada_en TIMESTAMP NOT NULL,
    vence_en TIMESTAMP NOT NULL,
    actualizada_en TIMESTAMP,
    CONSTRAINT uq_reserva_silla_activa UNIQUE (llave_activa),
    CONSTRAINT ck_reserva_estado CHECK (estado IN ('APARTADA', 'VENDIDA', 'LIBERADA'))
);

-- Índice para que el proceso de liberación encuentre rápido las reservas vencidas
CREATE INDEX IF NOT EXISTS idx_reserva_estado_vence ON reservas_silla (estado, vence_en);

-- ----------------------------------------------------------------------------
-- INSERCIÓN DE DATOS SEMILLA (Seed Data)
-- ----------------------------------------------------------------------------

-- Rutas troncales de la cooperativa
INSERT INTO rutas (id, codigo_ruta, origen, destino, distancia_km, duracion_estimada_horas) VALUES
(1, 'RUTA-BOG-MED', 'Bogotá D.C.', 'Medellín', 420.0, 8.5),
(2, 'RUTA-CAL-BOG', 'Cali', 'Bogotá D.C.', 460.0, 10.0),
(3, 'RUTA-MED-BUC', 'Medellín', 'Bucaramanga', 390.0, 7.5),
(4, 'RUTA-BOG-IBA', 'Bogotá D.C.', 'Ibagué', 210.0, 4.5)
ON CONFLICT (id) DO NOTHING;

-- Viajes programados para temporada alta
INSERT INTO viajes (id, codigo_viaje, ruta_id, fecha_salida, hora_salida, bus_placa, capacidad_total, precio_base, estado) VALUES
(1, 'VJ-2026-BOG-01', 1, CURRENT_DATE, '08:00:00', 'WOP-456', 40, 85000.00, 'PROGRAMADO'),
(2, 'VJ-2026-BOG-02', 1, CURRENT_DATE, '14:30:00', 'STR-123', 40, 90000.00, 'PROGRAMADO'),
(3, 'VJ-2026-CAL-01', 2, CURRENT_DATE + 1, '06:00:00', 'TLK-789', 40, 95000.00, 'PROGRAMADO'),
(4, 'VJ-2026-MED-01', 3, CURRENT_DATE + 1, '10:00:00', 'QWE-321', 40, 78000.00, 'PROGRAMADO')
ON CONFLICT (id) DO NOTHING;

-- Generación de 40 sillas para el Viaje 1 (Bogotá - Medellín)
-- Piso 1: Sillas 1 a 12 (Preferencial)
-- Piso 2: Sillas 13 a 40 (Estándar)
DO $$
DECLARE
    s INTEGER;
    p INTEGER;
    ub VARCHAR(20);
    est VARCHAR(20);
    pr NUMERIC(12,2);
BEGIN
    FOR s IN 1..40 LOOP
        IF s <= 12 THEN
            p := 1;
            pr := 95000.00;
        ELSE
            p := 2;
            pr := 85000.00;
        END IF;

        IF (s % 4 = 1 OR s % 4 = 0) THEN
            ub := 'VENTANA';
        ELSE
            ub := 'PASILLO';
        END IF;

        -- Simular algunas sillas ocupadas/reservadas para realismo
        IF s IN (3, 4, 12, 15, 16, 27) THEN
            est := 'OCUPADA';
        ELSIF s IN (7, 21) THEN
            est := 'RESERVADA';
        ELSE
            est := 'DISPONIBLE';
        END IF;

        INSERT INTO sillas (viaje_id, numero_silla, piso, ubicacion, estado, precio)
        VALUES (1, s, p, ub, est, pr)
        ON CONFLICT (viaje_id, numero_silla) DO NOTHING;
    END LOOP;
END $$;

-- Generación de 40 sillas para el Viaje 2
DO $$
DECLARE
    s INTEGER;
BEGIN
    FOR s IN 1..40 LOOP
        INSERT INTO sillas (viaje_id, numero_silla, piso, ubicacion, estado, precio)
        VALUES (2, s, CASE WHEN s <= 12 THEN 1 ELSE 2 END,
                CASE WHEN (s % 4 = 1 OR s % 4 = 0) THEN 'VENTANA' ELSE 'PASILLO' END,
                CASE WHEN s IN (1, 2, 5) THEN 'OCUPADA' ELSE 'DISPONIBLE' END,
                CASE WHEN s <= 12 THEN 100000.00 ELSE 90000.00 END)
        ON CONFLICT (viaje_id, numero_silla) DO NOTHING;
    END LOOP;
END $$;

-- Resetear secuencias
SELECT setval('rutas_id_seq', (SELECT MAX(id) FROM rutas));
SELECT setval('viajes_id_seq', (SELECT MAX(id) FROM viajes));
SELECT setval('sillas_id_seq', (SELECT MAX(id) FROM sillas));
