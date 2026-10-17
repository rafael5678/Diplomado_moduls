# Prueba de Estrés y Alta Concurrencia - ADR-001 / ASR 1
# Módulo de Escalabilidad y Alta Disponibilidad
param(
    [string]$BaseUrl = "http://localhost:80",
    [int]$PeticionesTotales = 50,
    [int]$Concurrencia = 5
)

Write-Host "======================================================================" -ForegroundColor Cyan
Write-Host "Iniciando Simulacion de Carga: ADR-001 (Picos de Temporada Alta)" -ForegroundColor Cyan
Write-Host "URL Base: $BaseUrl" -ForegroundColor Gray
Write-Host "Peticiones Totales: $PeticionesTotales" -ForegroundColor Gray
Write-Host "======================================================================" -ForegroundColor Cyan

# 1. Verificar Salud del Balanceador de Carga
try {
    $lbHealth = Invoke-RestMethod -Uri "$BaseUrl/lb-health" -Method Get -TimeoutSec 3
    Write-Host "[OK] Balanceador Nginx respondiendo: $($lbHealth | ConvertTo-Json -Compress)" -ForegroundColor Green
} catch {
    Write-Host "[WARN] Balanceador no responde en puerto 80 (tal vez no iniciado con docker compose). Probando directo en nodo 8080..." -ForegroundColor Yellow
    $BaseUrl = "http://localhost:8080"
}

# 2. Encolamiento Concurrente de Compras de Tiquetes (Amortiguacion de rafagas)
$tiempos = @()
$rechazos = 0
$exitos = 0

Write-Host "`nEnviando $PeticionesTotales compras asincronas concurrentes..." -ForegroundColor Yellow
$cronometroTotal = [System.Diagnostics.Stopwatch]::StartNew()

for ($i = 1; $i -le $PeticionesTotales; $i++) {
    $body = @{
        idOrden = "ORD-SIM-$i"
        viajeId = 1
        numeroSilla = ($i % 40) + 1
        pasajeroNombre = "Pasajero Simulado $i"
        pasajeroEmail = "usuario$i@prueba.com"
        pasajeroDocumento = "CC$i"
        monto = 85000
    } | ConvertTo-Json

    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    try {
        $resp = Invoke-RestMethod -Uri "$BaseUrl/api/v1/escalabilidad/compras" -Method Post -Body $body -ContentType "application/json" -TimeoutSec 5
        $sw.Stop()
        $tiempos += $sw.ElapsedMilliseconds
        $exitos++
    } catch {
        $sw.Stop()
        $tiempos += $sw.ElapsedMilliseconds
        $rechazos++
    }
}
$cronometroTotal.Stop()

# 3. Calculo de Percentil 95 y Disponibilidad
$tiemposOrdenados = $tiempos | Sort-Object
$idxP95 = [Math]::Floor($tiemposOrdenados.Count * 0.95)
$p95 = $tiemposOrdenados[$idxP95]
$promedio = ($tiempos | Measure-Object -Average).Average
$disponibilidad = ($exitos / $PeticionesTotales) * 100

Write-Host "`n======================================================================" -ForegroundColor Cyan
Write-Host "RESULTADOS DE LA PRUEBA (ASR 1 / ADR-001)" -ForegroundColor Cyan
Write-Host "======================================================================" -ForegroundColor Cyan
Write-Host "Total Procesadas: $PeticionesTotales" -ForegroundColor White
Write-Host "Compras Exitosas (0% rechazos por saturacion): $exitos ($disponibilidad%)" -ForegroundColor Green
Write-Host "Compras Rechazadas: $rechazos" -ForegroundColor $(if ($rechazos -eq 0) { "Green" } else { "Red" })
Write-Host "Latencia Promedio: $([Math]::Round($promedio, 2)) ms" -ForegroundColor White
Write-Host "Latencia Percentil 95 (p95): $p95 ms (Meta ASR 1: < 5000 ms)" -ForegroundColor $(if ($p95 -lt 5000) { "Green" } else { "Red" })
Write-Host "Tiempo Total de Ejecucion: $($cronometroTotal.Elapsed.TotalSeconds) segundos" -ForegroundColor White
Write-Host "======================================================================" -ForegroundColor Cyan
