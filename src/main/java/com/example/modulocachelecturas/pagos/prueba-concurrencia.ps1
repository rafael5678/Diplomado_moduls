
$url = "http://localhost:8081/cobros"
$llave = "prueba-concurrencia-139"

$cuerpo = @{
    reservaId = "reserva-concurrencia-139"
    documento = "1085"
    valorEnPesos = 240000
} | ConvertTo-Json -Compress

$trabajos = 1..2 | ForEach-Object {
    Start-Job -ScriptBlock {
        param($url, $llave, $cuerpo)

        try {
            $respuesta = Invoke-RestMethod `
                -Uri $url `
                -Method Post `
                -Headers @{"Idempotency-Key" = $llave} `
                -ContentType "application/json" `
                -Body $cuerpo

            [PSCustomObject]@{
                Resultado = "OK"
                Referencia = $respuesta.referencia
                Estado = $respuesta.estado
            }
        }
        catch {
            [PSCustomObject]@{
                Resultado = "ERROR"
                Referencia = ""
                Estado = $_.Exception.Message
            }
        }
    } -ArgumentList $url, $llave, $cuerpo
}

$trabajos | Wait-Job | Out-Null
$trabajos | Receive-Job
$trabajos | Remove-Job
