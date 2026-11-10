param(
    [ValidateSet('generate','import','import-source','verify','benchmark','show')]
    [string]$Command = 'show',
    [ValidateRange(1,1000000)][int]$Count = 1000,
    [ValidateRange(0,2147483647)][int]$Seed = 42,
    [ValidateRange(1,100000)][int]$Repeats = 200,
    [ValidateRange(0,100000)][int]$Warmup = 50
)
$ErrorActionPreference = 'Stop'
Push-Location (Split-Path $PSScriptRoot -Parent)
try {
    & .\mvnw.cmd -B compile org.codehaus.mojo:exec-maven-plugin:3.5.0:java "-Dexec.args=$Command $Count $Seed $Repeats $Warmup"
    if ($LASTEXITCODE -ne 0) { throw "Lab2 failed: $Command (exit $LASTEXITCODE)" }
} finally { Pop-Location }
