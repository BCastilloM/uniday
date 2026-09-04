# Uniday

Aplicación web de gestión de semestres y materias (Spring Boot + Thymeleaf + MySQL).

## Requisitos

- **Docker** (Docker Desktop en Windows): necesario para la base de datos MySQL. Debe estar **iniciado** antes de levantar el proyecto.
- **JDK 17** o superior.
- **Gradle** no hace falta instalarlo: el proyecto incluye el wrapper (`gradlew` / `gradlew.bat`).
- **PowerShell** para ejecutar el script `dev.ps1`.

## Puesta en marcha

Desde la raíz del proyecto, en PowerShell:

```powershell
# Si la primera vez da error de permisos de ejecución de scripts:
Set-ExecutionPolicy -Scope CurrentUser RemoteSigned

# Reinicio limpio: para MySQL + daemons y levanta todo otra vez
.\dev.ps1 restart
```

El script hace lo siguiente al ejecutar `restart`:

1. Detiene el contenedor MySQL (Docker) y los daemons de Gradle.
2. Termina cualquier proceso Java previo en el puerto 8080.
3. Levanta MySQL (Docker) de nuevo.
4. Arranca la aplicación Spring Boot.

> **Importante:** Docker debe estar corriendo. Si no arranca MySQL, abre Docker Desktop y ejecuta de nuevo `.\dev.ps1 restart`.

## Comandos del script

| Comando | Descripción                                             |
| ------- | ------------------------------------------------------- |
| `.\dev.ps1 restart` | Para todo y levanta limpio (MySQL + app). Es el default. |
| `.\dev.ps1 up`      | Solo levanta MySQL + app (sin parar antes).              |
| `.\dev.ps1 stop`    | Detiene MySQL (Docker) y los daemons de Gradle.          |

## Acceso

Una vez arrancada, abre el navegador en:

- **App:** http://localhost:8080

La primera vez se cargan datos demo automáticamente:

- **Email:** `demo@uniday.cl`
- **Contraseña:** `1234`

## Detener la app

- Para detener solo la aplicación, presiona **Ctrl+C** en la terminal donde corre `bootRun`.
- Para detener todo (MySQL + Gradle), ejecuta: `.\dev.ps1 stop`
