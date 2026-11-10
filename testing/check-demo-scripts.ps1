# No services needed: child Windows PowerShell processes mock Docker at its boundary.
param([string]$Case)
$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent
if (-not $Case) {
    $windowsPowerShell = Join-Path $env:SystemRoot 'System32/WindowsPowerShell/v1.0/powershell.exe'
    $cases = @('redis-ok','redis-native-error','redis-server-error','redis-late-error','mongo-ok','mongo-copy-error','mongo-shell-error')
    foreach ($probeCase in $cases) {
        $log = Join-Path $repo "target-lab2-probe-$probeCase.log"
        # Native stderr from expected failing children must not terminate this runner.
        $ErrorActionPreference = 'Continue'
        & $windowsPowerShell -NoProfile -File $PSCommandPath -Case $probeCase *> $log
        $code = $LASTEXITCODE
        $ErrorActionPreference = 'Stop'
        $expected = if ($probeCase -like '*-ok') { 0 } else { 1 }
        if ($code -ne $expected) { throw "$probeCase expected exit $expected, got $code; see $log" }
        Write-Output "PASS $probeCase exit=$code"
    }
    return
}
$global:demoProbeCase = $Case
$global:demoProbeCalls = 0
$global:demoExpected = @(
    'SET product:1 {"name":"Coffee","price":"120.00"}',
    'GET product:1', 'EXISTS product:1',
    'SET product:1 {"name":"Coffee","price":"125.00"}',
    'EXPIRE product:1 120', 'TTL product:1', 'DEL product:1', 'EXISTS product:1',
    'ZADD popular_products 12 Coffee 8 Tea 5 Chocolate',
    'ZINCRBY popular_products 3 Tea', 'ZRANGE popular_products 0 9 REV WITHSCORES'
)
function global:docker {
    $call = @($args)
    $global:demoProbeCalls++
    $global:LASTEXITCODE = 0
    if ($global:demoProbeCase -like 'redis-*') {
        $expected = @('compose','exec','-T','redis','redis-cli','-e','--raw','-n','15') + $global:demoExpected[$global:demoProbeCalls-1].Split(' ')
        if ($call.Count -ne $expected.Count) { throw "Wrong argument count: $($call.Count), expected $($expected.Count)" }
        for ($i=0; $i -lt $call.Count; $i++) {
            if ($call[$i] -cne $expected[$i]) { throw "Wrong argument ${i}: [$($call[$i])] expected [$($expected[$i])]" }
        }
        if ($global:demoProbeCase -eq 'redis-native-error') { $global:LASTEXITCODE=7; return 'forced Docker failure' }
        if ($global:demoProbeCase -eq 'redis-server-error') { return 'ERR forced server error with exit 0' }
        if ($global:demoProbeCase -eq 'redis-late-error' -and $call[9] -eq 'ZRANGE') { $global:LASTEXITCODE=1; return 'ERR forced ZRANGE failure' }
        return '1'
    }
    if ($call -contains 'redis' -or $call -contains 'ZRANGE') { throw 'Mongo script called Redis' }
    if ($global:demoProbeCalls -eq 1) {
        if (($call[0..2] -join ' ') -ne 'compose cp scripts/mongo-demo.js') { throw 'Unexpected Mongo copy arguments' }
        if ($global:demoProbeCase -eq 'mongo-copy-error') { $global:LASTEXITCODE=8 }
    } else {
        if (($call[0..5] -join ' ') -ne 'compose exec -T mongo sh -c') { throw 'Unexpected Mongo shell arguments' }
        if ($global:demoProbeCase -eq 'mongo-shell-error') { $global:LASTEXITCODE=9 }
    }
}
if ($Case -like 'redis-*') {
    & (Join-Path $repo 'scripts/redis-demo.ps1')
    if ($global:demoProbeCalls -ne 11) { throw 'Redis demo did not execute all 11 commands' }
} else {
    & (Join-Path $repo 'scripts/mongo-demo.ps1')
    if ($global:demoProbeCalls -ne 2) { throw 'Mongo demo did not execute copy and shell' }
}
