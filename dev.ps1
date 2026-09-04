# dev.ps1 - Script de desarrollo para el proyecto Uniday (Spring Boot + MySQL)
#
# Requisito: Docker debe estar corriendo (la base de datos MySQL corre en contenedor).
#
# Uso:
#   .\dev.ps1 stop      -> Detiene MySQL (Docker) y daemons de Gradle
#   .\dev.ps1 restart   -> Para todo y levanta limpio (MySQL + app)
#   .\dev.ps1 up        -> Solo levanta MySQL + app (sin parar antes)
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

function Start-Mysql {
    Write-Host "[3/3] Levantando MySQL (Docker)..." -ForegroundColor Cyan
    docker compose up -d 2>&1 | ForEach-Object { Write-Host "  $_" }
    Write-Host "  MySQL listo." -ForegroundColor Green
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
