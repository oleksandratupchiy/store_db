# Only the dedicated Compose Redis; DB 15 is reserved for this demo.
$ErrorActionPreference = 'Stop'
Push-Location (Split-Path $PSScriptRoot -Parent)
function Invoke-Redis {
    param([Parameter(Mandatory=$true)][string[]]$RedisArgs)
    Write-Host ('redis-cli -e --raw -n 15 ' + ($RedisArgs -join ' '))
    # Named array binding avoids ValueFromRemainingArguments nesting in Windows PS 5.1.
    # -e makes redis-cli return a failure code for Redis error replies as well.
    $dockerArgs = @('compose','exec','-T','redis','redis-cli','-e','--raw','-n','15') + $RedisArgs
    $reply = @(& docker @dockerArgs)
    $commandExitCode = $LASTEXITCODE
    $reply
    if ($commandExitCode -ne 0) {
        throw "Redis command $($RedisArgs[0]) failed (exit $commandExitCode)"
    }
    # Also reject an ERR reply if a wrapper unexpectedly reports exit 0.
    if ($reply | Where-Object { $_ -match '^(\(error\)\s*)?ERR\b' }) {
        throw "Redis command $($RedisArgs[0]) returned ERR"
    }
}
try {
    Invoke-Redis -RedisArgs @('SET','product:1','{"name":"Coffee","price":"120.00"}')
    Invoke-Redis -RedisArgs @('GET','product:1')
    Invoke-Redis -RedisArgs @('EXISTS','product:1')
    Invoke-Redis -RedisArgs @('SET','product:1','{"name":"Coffee","price":"125.00"}')
    Invoke-Redis -RedisArgs @('EXPIRE','product:1','120')
    Invoke-Redis -RedisArgs @('TTL','product:1')
    Invoke-Redis -RedisArgs @('DEL','product:1')
    Invoke-Redis -RedisArgs @('EXISTS','product:1')
    # ZADD resets the three fixed demo scores: repeated runs give the same ranking.
    Invoke-Redis -RedisArgs @('ZADD','popular_products','12','Coffee','8','Tea','5','Chocolate')
    Invoke-Redis -RedisArgs @('ZINCRBY','popular_products','3','Tea')
    Invoke-Redis -RedisArgs @('ZRANGE','popular_products','0','9','REV','WITHSCORES')
} finally { Pop-Location }
