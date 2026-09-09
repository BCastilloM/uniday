-- =============================================================================
-- Uniday - Esquema de base de datos MySQL
-- =============================================================================
-- Base:   uniday
-- Usuario de la app: uniday / uniday  (src/main/resources/application.properties)
-- Ejecutar: mysql -u root < sql/uniday.sql  (o abrir en MySQL Workbench)
-- La app usa ddl-auto=validate: el esquema debe coincidir con las entidades JPA.
-- =============================================================================

-- Fuerza UTF-8 para que los acentos se guarden bien (evita mojibake).
SET NAMES utf8mb4;

-- -----------------------------------------------------------------------------
-- 1. Base de datos y usuario (idempotente)
-- -----------------------------------------------------------------------------
CREATE DATABASE IF NOT EXISTS uniday
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

-- Usuario local y remoto para la app
CREATE USER IF NOT EXISTS 'uniday'@'localhost' IDENTIFIED BY 'uniday';
CREATE USER IF NOT EXISTS 'uniday'@'%' IDENTIFIED BY 'uniday';
GRANT ALL PRIVILEGES ON uniday.* TO 'uniday'@'localhost';
GRANT ALL PRIVILEGES ON uniday.* TO 'uniday'@'%';
FLUSH PRIVILEGES;

USE uniday;

-- -----------------------------------------------------------------------------
-- 2. Tablas
--    Tipos EXACTOS del modelo JPA (ddl-auto=validate), con FKs.
--    Resetear el esquema: DROP TABLE IF EXISTS materias, semestres, usuarios
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS usuarios (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    nombre          VARCHAR(255) NOT NULL,
    email           VARCHAR(255) NOT NULL,
    password        VARCHAR(255) NOT NULL,
    fecha_creacion  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_usuarios_email UNIQUE (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS semestres (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    usuario_id   BIGINT      NOT NULL,
    nombre       VARCHAR(255) NOT NULL,
    activo       BIT(1)      NOT NULL,
    fecha_inicio DATE        NULL,
    fecha_fin    DATE        NULL,
    PRIMARY KEY (id),
    KEY idx_semestres_usuario_id (usuario_id),
    CONSTRAINT fk_semestres_usuario
        FOREIGN KEY (usuario_id) REFERENCES usuarios (id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS materias (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    semestre_id        BIGINT       NOT NULL,
    nombre             VARCHAR(255) NOT NULL,
    profesor           VARCHAR(255) NULL,
    codigo             VARCHAR(50)  NULL,
    creditos           INT          NOT NULL,
    descripcion        TEXT         NULL,
    asistencia_exigida INT          NOT NULL,
    fecha_inicio_clases DATE        NULL,
    fecha_fin_clases    DATE        NULL,
    PRIMARY KEY (id),
    KEY idx_materias_semestre_id (semestre_id),
    CONSTRAINT fk_materias_semestre
        FOREIGN KEY (semestre_id) REFERENCES semestres (id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS dias_no_clase (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    semestre_id   BIGINT       NOT NULL,
    fecha_inicio  DATE         NOT NULL,
    fecha_fin     DATE         NOT NULL,
    motivo        VARCHAR(120) NULL,
    PRIMARY KEY (id),
    KEY idx_dias_no_clase_semestre_id (semestre_id),
    CONSTRAINT fk_dias_no_clase_semestre
        FOREIGN KEY (semestre_id) REFERENCES semestres (id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS asistencias (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    materia_id  BIGINT      NOT NULL,
    fecha       DATE        NOT NULL,
    presente    BOOLEAN     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_asistencias_materia_fecha (materia_id, fecha),
    KEY idx_asistencias_materia_id (materia_id),
    CONSTRAINT fk_asistencias_materia
        FOREIGN KEY (materia_id) REFERENCES materias (id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS horarios (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    materia_id  BIGINT       NOT NULL,
    dia_semana  INT          NOT NULL,
    hora_inicio TIME(6)      NOT NULL,
    hora_fin    TIME(6)      NOT NULL,
    sala        VARCHAR(100) NULL,
    PRIMARY KEY (id),
    KEY idx_horarios_materia_id (materia_id),
    CONSTRAINT fk_horarios_materia
        FOREIGN KEY (materia_id) REFERENCES materias (id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS actividades (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    materia_id  BIGINT       NOT NULL,
    titulo      VARCHAR(200) NOT NULL,
    tipo        VARCHAR(30)  NOT NULL,
    fecha       DATE         NOT NULL,
    estado      VARCHAR(20)  NOT NULL DEFAULT 'pendiente',
    PRIMARY KEY (id),
    KEY idx_actividades_materia_id (materia_id),
    CONSTRAINT fk_actividades_materia
        FOREIGN KEY (materia_id) REFERENCES materias (id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS notas (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    materia_id     BIGINT       NOT NULL,
    nota_padre_id  BIGINT       NULL,
    nombre         VARCHAR(255) NOT NULL,
    valor          DOUBLE       NOT NULL,
    ponderacion    DOUBLE       NOT NULL,
    PRIMARY KEY (id),
    KEY idx_notas_materia_id (materia_id),
    KEY idx_notas_nota_padre_id (nota_padre_id),
    CONSTRAINT fk_notas_materia
        FOREIGN KEY (materia_id) REFERENCES materias (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_notas_nota_padre
        FOREIGN KEY (nota_padre_id) REFERENCES notas (id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------------------------------
-- 3. Datos demo (opcionales)
--    Coinciden con config/DataSeeder.java (demo@uniday.cl / 1234).
--    Comenta esta sección si prefieres la BD vacía.
-- -----------------------------------------------------------------------------
INSERT INTO usuarios (id, nombre, email, password, fecha_creacion) VALUES
    (1, 'María González', 'demo@uniday.cl',
     '03ac674216f3e15c761ee1a5e255f067953623c8b388b4459e13f978d7c846f4', NOW());

INSERT INTO semestres (id, usuario_id, nombre, activo, fecha_inicio, fecha_fin) VALUES
    (1, 1, '1er Semestre 2026', 1, '2026-03-02', '2026-07-10'),
    (2, 1, '2do Semestre 2025', 0, '2025-08-04', '2025-12-12');

INSERT INTO materias (id, semestre_id, nombre, profesor, codigo, creditos, descripcion, asistencia_exigida) VALUES
    (1, 1, 'Programación Orientada a Objetos',   'Ing. Carlos Muñoz', 'INF-220', 5, 'Paradigma de orientación a objetos, herencia, polimorfismo y patrones de diseño en Java.', 80),
    (2, 1, 'Base de Datos',                      'Ing. Ana Reyes',    'INF-230', 5, 'Diseño relacional, normalización, SQL avanzado y fundamentos de transacciones ACID.', 75),
    (3, 1, 'Ingeniería de Software I',           'Ing. Pedro Soto',   'INF-240', 4, 'Ciclos de vida del software, metodologías ágiles Scrum y modelado UML.', 70),
    (4, 1, 'Redes de Computadores',              'Ing. Luis Fuentes', 'TEL-210', 4, 'Modelo OSI, TCP/IP, direccionamiento IPv4/IPv6, protocolos de capa de transporte y enlace.', 85),
    (5, 1, 'Estadística y Probabilidades',       'Prof. Claudia Vera', 'MAT-215', 4, 'Variables aleatorias, distribuciones de probabilidad, intervalos de confianza y pruebas de hipótesis.', 75),
    (6, 2, 'Estructuras de Datos',               'Ing. Carlos Muñoz', 'INF-120', 5, 'Listas enlazadas, árboles binarios, grafos y algoritmos de ordenamiento y búsqueda.', 80),
    (7, 2, 'Sistemas Operativos',                'Ing. Roberto Díaz', 'INF-130', 5, 'Gestión de procesos, memoria virtual, concurrencia, sincronización y sistemas de archivos.', 75),
    (8, 2, 'Inglés Técnico',                     'Prof. Sarah Mitchell', 'IDI-101', 3, 'Lectura comprensiva de documentación técnica y redacción de especificaciones en inglés.', 70);

-- Horarios del semestre 1 (materias 1 a 5)
INSERT INTO horarios (id, materia_id, dia_semana, hora_inicio, hora_fin, sala) VALUES
    (1, 1, 1, '08:30', '10:00', 'Sala 12'),
    (2, 1, 3, '08:30', '10:00', 'Sala 12'),
    (3, 2, 2, '10:15', '11:45', 'Lab 3'),
    (4, 3, 4, '14:00', '15:30', 'Sala 7'),
    (5, 4, 5, '08:30', '10:00', 'Lab 1'),
    (6, 5, 2, '14:00', '15:30', 'Sala 4');

-- Actividades del semestre 1 (materias 1 a 4), fechas cercanas a hoy (septiembre 2026)
INSERT INTO actividades (id, materia_id, titulo, tipo, fecha, estado) VALUES
    (1, 1, 'Certamen 1 - Programación',              'certamen',     '2026-09-09', 'pendiente'),
    (2, 1, 'Tarea: Informe de herencia',             'tarea',        '2026-09-09', 'completada'),
    (3, 2, 'Test: Normalización',                    'test',         '2026-09-09', 'pendiente'),
    (4, 4, 'Tarea: Direccionamiento IPv4',           'tarea',        '2026-09-04', 'progreso'),
    (5, 3, 'Presentación: Scrum en acción',          'presentación', '2026-09-15', 'pendiente');

-- Notas del semestre 1 (materias 1 y 2), con jerarquía PADRE/HIJA
-- (Certamen 1 de POO tiene 2 partes; el resto son padres sin partes)
INSERT INTO notas (id, materia_id, nota_padre_id, nombre, valor, ponderacion) VALUES
    (1, 1, NULL, 'Certamen 1', 6.0, 30),
    (2, 1, NULL, 'Tarea 1', 5.8, 15),
    (3, 1, 1, 'Parte A', 6.5, 50),
    (4, 1, 1, 'Parte B', 5.5, 50),
    (5, 2, NULL, 'Certamen 1', 5.5, 25),
    (6, 2, NULL, 'Test 1', 6.0, 15);