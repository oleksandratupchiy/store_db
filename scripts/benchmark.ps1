param([int[]]$Sizes = @(1000,10000,100000), [int]$Seed=42, [int]$Repeats=200, [int]$Warmup=50,
      [switch]$Reuse)
$ErrorActionPreference = 'Stop'
Push-Location (Split-Path $PSScriptRoot -Parent)
try {
    $sizeList = $Sizes -join ','
    $reuseText = $Reuse.IsPresent.ToString().ToLowerInvariant()
    & .\mvnw.cmd -B compile org.codehaus.mojo:exec-maven-plugin:3.5.0:java "-Dexec.args=suite $sizeList $Seed $Repeats $Warmup $reuseText"
    if ($LASTEXITCODE -ne 0) { throw "Benchmark suite failed (exit $LASTEXITCODE)" }
} finally { Pop-Location }
