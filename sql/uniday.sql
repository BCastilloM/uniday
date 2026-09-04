-- =============================================================================
-- Uniday - Esquema de base de datos MySQL (levantamiento clásico)
-- =============================================================================
-- Motor:  MySQL 8.0 / MariaDB (Workbench, XAMPP o MySQL local)
-- Base:   uniday
-- Usuario de la app: uniday / uniday  (coincide con src/main/resources/application.properties)
--
-- CÓMO USARLO (elegí una opción):
--
--   Opción A - XAMPP (PowerShell o cmd, root normalmente sin contraseña):
--     & "C:\xampp\mysql\bin\mysql.exe" -u root < sql/uniday.sql
--
--   Opción B - MySQL Workbench: File -> Open SQL Script -> ejecutar (Ctrl+Shift+Enter)
--
--   Opción C - MySQL local con contraseña de root:
--     mysql -u root -p < sql/uniday.sql
--
--   Después, levantar la aplicación:
--     .\gradlew.bat bootRun
--
--   La aplicación arranca con ddl-auto=validate: NO crea ni modifica el esquema.
--   Si las tablas no existen, el arranque falla hasta que ejecutes este script.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. Base de datos y usuario (idempotente: si ya existen, no hace nada)
-- -----------------------------------------------------------------------------
CREATE DATABASE IF NOT EXISTS uniday
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

-- Garantiza que la app pueda conectar por TCP localhost y por cualquier host
CREATE USER IF NOT EXISTS 'uniday'@'localhost' IDENTIFIED BY 'uniday';
CREATE USER IF NOT EXISTS 'uniday'@'%' IDENTIFIED BY 'uniday';
GRANT ALL PRIVILEGES ON uniday.* TO 'uniday'@'localhost';
GRANT ALL PRIVILEGES ON uniday.* TO 'uniday'@'%';
FLUSH PRIVILEGES;

USE uniday;

-- -----------------------------------------------------------------------------
-- 2. Tablas
--    Los tipos de columna coinciden EXACTAMENTE con los que Hibernate espera
--    (spring.jpa.hibernate.ddl-auto=validate), para que el arranque valide sin
--    quejas. Las FKs son adicionales: el modelo JPA actual las maneja como
--    relaciones lógicas (solo IDs), por lo que agregarlas aquí da integridad
--    referencial real sin romper el código.
--
--    Para RESETEAR el esquema por completo (borra todos los datos), ejecutar
--    primero, con cuidado:
--      DROP TABLE IF EXISTS materias;
--      DROP TABLE IF EXISTS semestres;
--      DROP TABLE IF EXISTS usuarios;
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
    color_hex          VARCHAR(255) NULL,
    asistencia_exigida INT          NOT NULL,
    PRIMARY KEY (id),
    KEY idx_materias_semestre_id (semestre_id),
    CONSTRAINT fk_materias_semestre
        FOREIGN KEY (semestre_id) REFERENCES semestres (id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------------------------------
-- 3. Datos demo (OPCIONALES)
--    Coinciden 1:1 con lo que insertaba config/DataSeeder.java (hoy desactivado
--    por defecto: app.seed.enabled=false). Si prefieres la BD vacía, comenta
--    toda esta sección: la app arranca igual y podrás registrarte desde la web.
--    La contraseña de demo@uniday.cl es "1234" hasheada con SHA-256 sin salt
--    (PasswordUtils), exactamente como la crearía el seeder.
-- -----------------------------------------------------------------------------
INSERT INTO usuarios (id, nombre, email, password, fecha_creacion) VALUES
    (1, 'María González', 'demo@uniday.cl',
     '03ac674216f3e15c761ee1a5e255f067953623c8b388b4459e13f978d7c846f4', NOW());

INSERT INTO semestres (id, usuario_id, nombre, activo, fecha_inicio, fecha_fin) VALUES
    (1, 1, '1er Semestre 2026', 1, '2026-03-02', '2026-07-10'),
    (2, 1, '2do Semestre 2025', 0, '2025-08-04', '2025-12-12');

INSERT INTO materias (id, semestre_id, nombre, profesor, codigo, creditos, descripcion, color_hex, asistencia_exigida) VALUES
    (1, 1, 'Programación Orientada a Objetos',   'Ing. Carlos Muñoz', 'INF-220', 5, 'Paradigma de orientación a objetos, herencia, polimorfismo y patrones de diseño en Java.',                                  '#4A90D9', 80),
    (2, 1, 'Base de Datos',                      'Ing. Ana Reyes',    'INF-230', 5, 'Diseño relacional, normalización, SQL avanzado y fundamentos de transacciones ACID.',                                         '#7BC67E', 75),
    (3, 1, 'Ingeniería de Software I',           'Ing. Pedro Soto',   'INF-240', 4, 'Ciclos de vida del software, metodologías ágiles Scrum y modelado UML.',                                                     '#E8A838', 70),
    (4, 1, 'Redes de Computadores',              'Ing. Luis Fuentes', 'TEL-210', 4, 'Modelo OSI, TCP/IP, direccionamiento IPv4/IPv6, protocolos de capa de transporte y enlace.',                               '#D9534F', 85),
    (5, 1, 'Estadística y Probabilidades',       'Prof. Claudia Vera', 'MAT-215', 4, 'Variables aleatorias, distribuciones de probabilidad, intervalos de confianza y pruebas de hipótesis.',                  '#9B59B6', 75),
    (6, 2, 'Estructuras de Datos',               'Ing. Carlos Muñoz', 'INF-120', 5, 'Listas enlazadas, árboles binarios, grafos y algoritmos de ordenamiento y búsqueda.',                                       '#3498DB', 80),
    (7, 2, 'Sistemas Operativos',                'Ing. Roberto Díaz', 'INF-130', 5, 'Gestión de procesos, memoria virtual, concurrencia, sincronización y sistemas de archivos.',                                '#E67E22', 75),
    (8, 2, 'Inglés Técnico',                     'Prof. Sarah Mitchell', 'IDI-101', 3, 'Lectura comprensiva de documentación técnica y redacción de especificaciones en inglés.',                                '#1ABC9C', 70);