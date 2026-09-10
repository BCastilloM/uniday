# Uniday

Aplicación web de gestión de semestres y materias (Spring Boot + Thymeleaf + MySQL).

## Cómo se crea la base de datos

- La aplicación **NO crea ni modifica la BD** en tiempo de ejecución:
  `spring.jpa.hibernate.ddl-auto=validate` — la app solo comprueba que el esquema
  existente coincida con las entidades y **falla el arranque** si no es así.
- El esquema completo (BD, tablas, columnas, FKs y datos de ejemplo) se crea
  **únicamente** ejecutando el script **`sql/uniday.sql`**.
- Base de datos: `uniday` · Usuario de la app: `uniday` / `uniday`.
- El script crea **7 tablas**: `usuarios`, `semestres`, `materias`, `asistencias`,
  `horarios`, `actividades` y `notas`, con sus FKs.

> **Idempotencia:** la parte de esquema (BD, usuario y tablas) es idempotente
> (`IF NOT EXISTS`), pero **la sección 3 de datos demo NO**: usa IDs explícitos,
> por lo que ejecutar el script dos veces **falla por clave duplicada**. Si necesitas
> reejecutarlo, comenta la sección 3 o hazlo sobre una BD vacía.

## Requisitos

- **JDK 17** o superior.
- **Gradle** no hace falta instalarlo: el proyecto incluye el wrapper (`gradlew` / `gradlew.bat`).
- Un motor MySQL, o Docker:
  - **MySQL local**: XAMPP (incluye MariaDB), MySQL Workbench o cualquier MySQL 8 instalado localmente.
  - **Docker**: solo si usas el flujo de desarrollo con `dev.ps1` (el contenedor trae MySQL 8).

## Flujo rápido con dev.ps1 (recomendado, requiere Docker)

`dev.ps1` levanta MySQL en Docker y arranca la app de un solo comando:

```powershell
.\dev.ps1 restart   # o `up` si ya está todo levantado
```

- Requiere **Docker corriendo**.
- Levanta solo el contenedor de MySQL (`uniday-mysql`, puerto `3306`) y luego ejecuta `gradlew.bat bootRun`.
- En el primer arranque aplica `sql/uniday.sql` automáticamente (volumen vacío).
- **OJO:** si `sql/uniday.sql` cambia, `dev.ps1` detecta el hash distinto y **recrea el volumen de MySQL desde cero** — se pierden los datos guardados y vuelven los demo.
- Para detener todo: `.\dev.ps1 stop`.

Acciones: `stop` (detiene MySQL y daemons de Gradle), `up` (levanta sin parar antes) y `restart` (para todo y levanta limpio, por defecto).

## Levantar el proyecto (desarrollo local, sin Docker)

1. **Levanta el motor MySQL**:
   - XAMPP: abre el panel de control e inicia **MySQL**.
   - Workbench: conecta a tu servidor MySQL local (`localhost:3306`).

2. **Crea la base de datos ejecutando el script** `sql/uniday.sql` con usuario root:

   - **XAMPP** (PowerShell o cmd):
     ```powershell
     & "C:\xampp\mysql\bin\mysql.exe" -u root < sql/uniday.sql
     ```
   - **Workbench**: `File → Open SQL Script` → selecciona `sql/uniday.sql` → ejecuta (Ctrl+Shift+Enter).

   Crea: BD `uniday`, usuario `uniday`/`uniday`, las 7 tablas con sus FKs y los datos de ejemplo.

3. **Arranca la aplicación** desde la raíz del proyecto:
   ```powershell
   .\gradlew.bat bootRun
   ```

4. Abre **http://localhost:8080**

## Datos de ejemplo

El script `sql/uniday.sql` incluye en su sección 3 (opcional) los datos demo:

- **Email:** `demo@uniday.cl`
- **Contraseña:** `1234`
- 2 semestres y 8 materias de ejemplo.

Si comentas esa sección, la app arranca con la BD vacía: regístrate desde la web
y crea tus propios semestres y materias.

## Despliegue en servidor (Docker)

El `docker-compose.yml` levanta MySQL + la aplicación completa en un servidor:

```powershell
docker compose up -d --build
```

- El contenedor de MySQL ejecuta `sql/uniday.sql` automáticamente en el primer
  arranque del volumen (directorio `/docker-entrypoint-initdb.d`).
- La app queda disponible en **http://servidor:8080**.
- ⚠️ `docker compose down -v` elimina el volumen con todos los datos.

## Detener la app

- Presiona **Ctrl+C** en la terminal donde corre `bootRun`.