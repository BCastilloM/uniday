# dev.ps1 - Script de desarrollo para el proyecto Uniday (Spring Boot + MySQL)
#
# Requisito: Docker debe estar corriendo (la base de datos MySQL corre en contenedor).
#
# Uso:
#   .\dev.ps1 stop      -> Detiene MySQL (Docker) y daemons de Gradle
#   .\dev.ps1 restart   -> Para todo y levanta limpio (MySQL + app)
#   .\dev.ps1 up        -> Solo levanta MySQL + app (sin parar antes)
#
# Esquema automático: si sql/uniday.sql cambió (nueva tabla, columna, dato
# demo), dev.ps1 detecta el hash distinto y recrea el volumen de MySQL solo,
# para que la BD siempre coincida con el esquema (ddl-auto=validate).
#
# Consejo: ejecutar con PowerShell. Si da error de ejecución de scripts, correr:
#   Set-ExecutionPolicy -Scope Process Bypass
# y volver a ejecutar.

param(
    [Parameter(Position = 0)]
    [ValidateSet('stop', 'restart', 'up')]
    [string]$Action = 'restart'
)

# Nota: NO usamos $ErrorActionPreference='Stop' de forma global, porque PowerShell
# lanza NativeCommandError ante cualquier output de stderr de Docker/Gradle y
# aborta el script. En su lugar manejamos errores de forma explícita.
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $ScriptDir

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------
function Stop-AppPort([int]$Port = 8080) {
    $conns = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
    if ($conns) {
        foreach ($c in $conns) {
            $proc = Get-Process -Id $c.OwningProcess -ErrorAction SilentlyContinue
            if ($proc -and $proc.ProcessName -match 'java') {
                Write-Host "  -> Matando proceso anterior en puerto $Port (PID $($c.OwningProcess))" -ForegroundColor Yellow
                Stop-Process -Id $c.OwningProcess -Force
            }
        }
    } else {
        Write-Host "  -> Puerto $Port libre" -ForegroundColor Green
    }
}

function Stop-Mysql {
    Write-Host "`n[1/3] Deteniendo MySQL (Docker)..." -ForegroundColor Cyan
    docker compose down 2>&1 | ForEach-Object { Write-Host "  $_" }
    Write-Host "  MySQL detenido." -ForegroundColor Green
}

function Stop-Gradle {
    Write-Host "[2/3] Deteniendo daemons de Gradle..." -ForegroundColor Cyan
    if (Test-Path "$ScriptDir\gradlew.bat") {
        & "$ScriptDir\gradlew.bat" --stop 2>&1 | ForEach-Object { Write-Host "  $_" }
    }
    Write-Host "  Daemons detenidos." -ForegroundColor Green
}

# Hash del esquema aplicado en la BD (evita recrear el volumen si no cambió nada).
$MarkerFile = "$ScriptDir\.uniday-db-hash"

function Get-UnidaySqlHash {
    $hash = Get-FileHash -Algorithm SHA256 -LiteralPath "$ScriptDir\sql\uniday.sql"
    return $hash.Hash
}

# Recrea el volumen de MySQL solo cuando sql/uniday.sql cambió. Docker ejecuta
# el initdb únicamente sobre un volumen VACÍO, así que si agregamos tablas al
# SQL (nueva funcionalidad) y no borramos el volumen, la app cae al arrancar
# con ddl-auto=validate. Este check evita ese error de forma automática.
function Sync-MysqlSchema {
    $sqlHash = Get-UnidaySqlHash

    $marker = ''
    if (Test-Path -LiteralPath $MarkerFile) {
        $marker = (Get-Content -LiteralPath $MarkerFile -Raw).Trim()
    }

    docker volume inspect uniday_mysql-data 2>$null | Out-Null
    $volumeExists = ($LASTEXITCODE -eq 0)

    if ($volumeExists -and $marker -eq $sqlHash) {
        Write-Host "  Esquema vigente (sql/uniday.sql sin cambios)." -ForegroundColor Green
        return
    }

    if ($volumeExists) {
        Write-Host "  Cambios detectados en sql/uniday.sql -> recreando la BD..." -ForegroundColor Yellow
        docker compose down 2>&1 | ForEach-Object { Write-Host "  $_" }
        docker volume rm uniday_mysql-data 2>&1 | ForEach-Object { Write-Host "  $_" }
    } else {
        Write-Host "  Primera vez o volumen inexistente -> BD se crea desde cero." -ForegroundColor Yellow
    }
}

function Start-Mysql {
    Write-Host "[3/3] Levantando MySQL (Docker)..." -ForegroundColor Cyan
    # Solo el contenedor de BD: el servicio 'app' del compose ocupa el 8080 y
    # chocaría con el bootRun local que arranca Start-App.
    Sync-MysqlSchema
    docker compose up -d mysql 2>&1 | ForEach-Object { Write-Host "  $_" }
    if ($LASTEXITCODE -ne 0) {
        Write-Host "  ERROR: docker compose up falló. Revisa que Docker esté corriendo." -ForegroundColor Red
        exit 1
    }
    if (-not (Wait-MysqlReady)) {
        Write-Host "  ERROR: MySQL no quedó listo. Revisa los logs con: docker logs uniday-mysql" -ForegroundColor Red
        exit 1
    }
    # Guarda el hash del esquema aplicado para detectar cambios en el futuro.
    Get-UnidaySqlHash | Set-Content -LiteralPath $MarkerFile
    Write-Host "  MySQL listo." -ForegroundColor Green
}

# Espera el healthcheck de MySQL (docker-compose.yml). Cuando el volumen se
# recrea, mysqld parte de cero y tarda ~20-40s; si bootRun arranca antes,
# HikariCP falla al conectar y la app muere. Este wait evita ese error.
function Wait-MysqlReady {
    Write-Host "  Esperando que MySQL este listo..." -ForegroundColor Yellow
    for ($i = 1; $i -le 30; $i++) {
        $status = docker inspect --format '{{.State.Health.Status}}' uniday-mysql 2>$null
        if ($status -eq 'healthy') {
            Write-Host "  MySQL saludable." -ForegroundColor Green
            return $true
        }
        Start-Sleep -Seconds 2
    }
    return $false
}

function Start-App {
    Write-Host "`nArrancando la aplicacion Spring Boot (bootRun)..." -ForegroundColor Cyan
    Write-Host "  Esperando que arranque. Para detenerla: Ctrl+C"
    & "$ScriptDir\gradlew.bat" bootRun
}

# ---------------------------------------------------------------------------
# Acciones
# ---------------------------------------------------------------------------
switch ($Action) {
    'stop' {
        Write-Host "=== Uniday: deteniendo todo ===" -ForegroundColor Magenta
        Stop-Mysql
        Stop-Gradle
        Stop-AppPort
        Write-Host "`nTodo detenido." -ForegroundColor Green
    }
    'up' {
        Write-Host "=== Uniday: levantando ===" -ForegroundColor Magenta
        Start-Mysql
        Start-App
    }
    default {
        Write-Host "=== Uniday: reinicio limpio ===" -ForegroundColor Magenta
        Stop-Mysql
        Stop-Gradle
        Stop-AppPort
        Start-Mysql
        Start-App
    }
}
