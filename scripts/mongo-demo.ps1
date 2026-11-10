$ErrorActionPreference = 'Stop'
Push-Location (Split-Path $PSScriptRoot -Parent)
try {
    & docker compose cp scripts/mongo-demo.js mongo:/tmp/lab2-demo.js
    if ($LASTEXITCODE -ne 0) { throw "Could not copy Mongo demo (exit $LASTEXITCODE)" }
    & docker compose exec -T mongo sh -c 'if command -v mongosh >/dev/null 2>&1; then mongosh --quiet /tmp/lab2-demo.js; else mongo --quiet /tmp/lab2-demo.js; fi'
    if ($LASTEXITCODE -ne 0) { throw "Mongo demo failed (exit $LASTEXITCODE)" }
} finally { Pop-Location }
