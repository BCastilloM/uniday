# Uniday

Aplicación web de gestión de semestres y materias (Spring Boot + Thymeleaf + MySQL).

## Cómo se crea la base de datos

- La aplicación **NO crea ni modifica la BD** en tiempo de ejecución:
  `spring.jpa.hibernate.ddl-auto=validate` — la app solo comprueba que el esquema
  existente coincida con las entidades y **falla el arranque** si no es así.
- El esquema completo (BD, tablas, columnas, FKs y datos de ejemplo) se crea
  **únicamente** ejecutando el script **`sql/uniday.sql`**.
- Base de datos: `uniday` · Usuario de la app: `uniday` / `uniday`.

## Requisitos

- **JDK 17** o superior.
- **Gradle** no hace falta instalarlo: el proyecto incluye el wrapper (`gradlew` / `gradlew.bat`).
- Un motor MySQL, uno de estos:
  - **MySQL Workbench** con un servidor MySQL local, o
  - **XAMPP** (incluye MariaDB), o
  - cualquier MySQL 8 instalado localmente.
- **Docker**: solo para el despliegue en servidor, no es necesario en desarrollo.

## Levantar el proyecto (desarrollo local)

1. **Levanta el motor MySQL**:
   - XAMPP: abre el panel de control e inicia **MySQL**.
   - Workbench: conecta a tu servidor MySQL local (`localhost:3306`).

2. **Crea la base de datos ejecutando el script** `sql/uniday.sql` con usuario root:

   - **XAMPP** (PowerShell o cmd):
     ```powershell
     & "C:\xampp\mysql\bin\mysql.exe" -u root < sql/uniday.sql
     ```
   - **Workbench**: `File → Open SQL Script` → selecciona `sql/uniday.sql` → ejecuta (Ctrl+Shift+Enter).

   El script es **idempotente** (IF NOT EXISTS): puedes ejecutarlo varias veces sin romper nada.
   Crea: BD `uniday`, usuario `uniday`/`uniday`, tablas (`usuarios`, `semestres`, `materias`)
   con sus FKs, y los datos de ejemplo.

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