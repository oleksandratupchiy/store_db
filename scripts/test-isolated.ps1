# Full lab1 + lab2 regression against dedicated Compose PostgreSQL only.
param([switch]$Reuse)
$ErrorActionPreference = 'Stop'
Push-Location (Split-Path $PSScriptRoot -Parent)
$names = @('DB_URL','DB_USER','DB_PASSWORD','SPRING_FLYWAY_DEFAULT_SCHEMA','SPRING_FLYWAY_LOCATIONS','LAB2_PG_URL','LAB2_PG_USER')
$saved = @{}
foreach ($name in $names) { $saved[$name] = [Environment]::GetEnvironmentVariable($name,'Process') }
try {
    $port = if ($env:LAB2_PG_PORT) { $env:LAB2_PG_PORT } else { '55432' }
    $env:LAB2_PG_URL = "jdbc:postgresql://localhost:$port/store_db_benchmark"
    $env:LAB2_PG_USER = 'lab2'
    if (-not $Reuse) { & .\scripts\lab2.ps1 generate -Count 10 -Seed 900001 }
    Get-Content testing/lab1-fixtures.sql -Raw | docker compose exec -T postgres-bench psql -v ON_ERROR_STOP=1 -U lab2 -d store_db_benchmark
    if ($LASTEXITCODE -ne 0) { throw 'Regression fixtures failed' }
    $env:DB_URL = $env:LAB2_PG_URL + '?currentSchema=lab2_n10_s900001'
    $env:DB_USER = 'lab2'
    $env:DB_PASSWORD = if ($env:LAB2_PG_PASSWORD) { $env:LAB2_PG_PASSWORD } else { 'lab2_local_only' }
    $env:SPRING_FLYWAY_DEFAULT_SCHEMA = 'lab2_n10_s900001'
    $env:SPRING_FLYWAY_LOCATIONS = 'classpath:db/migration,classpath:db/lab2'
    & .\mvnw.cmd -B test
    if ($LASTEXITCODE -ne 0) { throw 'Regression tests failed' }
} finally {
    foreach ($name in $names) { [Environment]::SetEnvironmentVariable($name,$saved[$name],'Process') }
    Pop-Location
}
