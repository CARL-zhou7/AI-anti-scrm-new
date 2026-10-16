# Simple phase 2 smoke check against an isolated local MySQL 8.0.45 instance.
# Run with PowerShell 7 after building backend/target/anti-scam-backend-0.1.0.jar.
param([string]$MySqlBin = 'C:/Program Files/MySQL/MySQL Server 8.0/bin',
      [string]$Java = 'C:/Users/lenovo/.jdks/corretto-21.0.8/bin/java.exe')
$ErrorActionPreference = 'Stop'
$workspace = Split-Path $PSScriptRoot -Parent
$runDir = Join-Path $workspace ('.tools/content-smoke-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $runDir | Out-Null
$db = $null; $app = $null
$envNames = @('MYSQL_PWD','DB_URL','DB_USERNAME','DB_PASSWORD','ADMIN_USERNAME','ADMIN_PASSWORD','AVATAR_STORE')
$saved = @{}
foreach ($name in $envNames) { $saved[$name] = [Environment]::GetEnvironmentVariable($name) }
function Assert-That($condition, $message) { if (-not $condition) { throw $message } }
try {
    foreach ($port in @(13308,18081)) {
        $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, $port)
        try { $listener.Start() } finally { $listener.Stop() }
    }
    [Environment]::SetEnvironmentVariable('MYSQL_PWD', $null)
    & "$MySqlBin/mysqld.exe" --no-defaults --initialize-insecure "--datadir=$runDir/data" 2> "$runDir/init.log"
    Assert-That ($LASTEXITCODE -eq 0) 'MySQL initialization failed'
    $db = Start-Process "$MySqlBin/mysqld.exe" -ArgumentList @('--no-defaults',"--datadir=$runDir/data",'--port=13308','--bind-address=127.0.0.1','--mysqlx=0','--character-set-server=utf8mb4','--collation-server=utf8mb4_0900_bin','--console') -WindowStyle Hidden -PassThru -RedirectStandardOutput "$runDir/mysql.out.log" -RedirectStandardError "$runDir/mysql.err.log"
    $ready = $false
    for ($i=0; $i -lt 60; $i++) {
        if ($db.HasExited) { throw 'Isolated MySQL exited; no database changes made' }
        & "$MySqlBin/mysqladmin.exe" --no-defaults --host=127.0.0.1 --port=13308 --user=root ping 2>$null | Out-Null
        if ($LASTEXITCODE -eq 0) { $ready = $true; break }
        Start-Sleep -Milliseconds 500
    }
    Assert-That $ready 'Temporary MySQL did not start'
    $env:DB_PASSWORD = [guid]::NewGuid().ToString('N')
    "ALTER USER 'root'@'localhost' IDENTIFIED BY '$env:DB_PASSWORD'; CREATE DATABASE content_smoke CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;" | & "$MySqlBin/mysql.exe" --no-defaults --host=127.0.0.1 --port=13308 --user=root
    Assert-That ($LASTEXITCODE -eq 0) 'Database setup failed'
    $env:MYSQL_PWD = $env:DB_PASSWORD
    $env:DB_USERNAME = 'root'
    $env:DB_URL = 'jdbc:mysql://127.0.0.1:13308/content_smoke?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true'
    $env:ADMIN_USERNAME = 'ContentAdmin'
    $env:ADMIN_PASSWORD = [guid]::NewGuid().ToString('N').Substring(0,24)
    $env:AVATAR_STORE = Join-Path $workspace 'backend/var'
    $app = Start-Process $Java -WorkingDirectory "$workspace/backend" -ArgumentList @('-jar',"$workspace/backend/target/anti-scam-backend-0.1.0.jar",'--spring.profiles.active=dev','--server.port=18081') -WindowStyle Hidden -PassThru -RedirectStandardOutput "$runDir/app.out.log" -RedirectStandardError "$runDir/app.err.log"
    $script:client = [Microsoft.PowerShell.Commands.WebRequestSession]::new()
    $ready = $false
    for ($i=0; $i -lt 90; $i++) {
        if ($app.HasExited) { throw "Backend exited; inspect $runDir/app.out.log" }
        try { Invoke-WebRequest 'http://127.0.0.1:18081/anti.scam/v1/auth/csrf' -WebSession $script:client | Out-Null; $ready=$true; break }
        catch { Start-Sleep -Milliseconds 500 }
    }
    Assert-That $ready 'Backend did not start'
    function Api($path, $status=200, $method='GET', $body=$null, $token=$null) {
        $options = @{Uri="http://127.0.0.1:18081/anti.scam/v1$path"; Method=$method; WebSession=$script:client; SkipHttpErrorCheck=$true}
        if ($null -ne $body) { $options.Body = [Text.Encoding]::UTF8.GetBytes(($body | ConvertTo-Json)); $options.ContentType='application/json;charset=UTF-8' }
        if ($token) { $options.Headers=@{'X-CSRF-TOKEN'=$token} }
        $response = Invoke-WebRequest @options
        Assert-That ($response.StatusCode -eq $status) "$method $path returned $($response.StatusCode), expected $status"
        Assert-That (($response.Headers['Cache-Control'] -join ',') -match 'no-store') 'Missing no-store'
        return ($response.Content | ConvertFrom-Json)
    }
    $paths = @('/scenarios','/scammer-profiles','/victim-profiles','/scammer-profiles/1','/victim-profiles/1','/avatars/missing')
    foreach ($path in $paths) { Api $path 401 | Out-Null }
    $token = (Api '/auth/csrf').data.token
    Api '/auth/register' 201 POST @{username='ContentUser';password='123456';confirmPassword='123456'} $token | Out-Null
    Api '/auth/login' 200 POST @{username='ContentUser';password='123456'} $token | Out-Null
    $scenarios = (Api '/scenarios').data
    Assert-That ($scenarios.Count -eq 3) 'Expected three scenarios'
    Assert-That (($scenarios.code -join ',') -eq 'TASK_REBATE,FAKE_INVESTMENT,IMPERSONATION_LOAN') 'Scenario codes differ'
    $first = (Api '/scammer-profiles?limit=1').data
    Assert-That ($first.items.Count -eq 1 -and $first.hasMore -and $first.items[0].id -is [string]) 'First page invalid'
    $last = (Api ('/scammer-profiles?limit=1&cursor=' + $first.nextCursor)).data
    Assert-That ($last.items.Count -eq 1 -and -not $last.hasMore -and $null -eq $last.nextCursor -and $last.items[0].id -ne $first.items[0].id) 'Last page invalid'
    Assert-That ((Api ('/victim-profiles?cursor=' + $first.nextCursor) 400).code -eq 'INVALID_CURSOR') 'Cross-resource cursor accepted'
    foreach ($bad in @('/scammer-profiles?enabled=false','/scammer-profiles?limit=0','/scammer-profiles/007','/scenarios?limit=1')) { Api $bad 400 | Out-Null }
    $s = (Api '/scammer-profiles').data.items
    $v = (Api '/victim-profiles').data.items
    Assert-That ($s.Count -eq 2 -and $v.Count -eq 3) 'Expected five profiles'
    foreach ($pair in @(@('scammer-profiles',$s),@('victim-profiles',$v))) {
        foreach ($profile in $pair[1]) {
            $detail = (Api ('/' + $pair[0] + '/' + $profile.id)).data
            Assert-That ($detail.name -eq $profile.name -and $detail.contextTime -and $detail.contextPlace -and $detail.currentState) 'Incomplete profile'
            Assert-That (-not ($detail.PSObject.Properties.Name -match '^(enabled|rowVersion|createdAt|updatedAt)$')) 'Admin fields exposed'
            Assert-That ($detail.avatar.key -match '^[a-f0-9]{64}$') 'Invalid public avatar key'
            $image = Invoke-WebRequest ("http://127.0.0.1:18081" + $detail.avatar.url) -WebSession $script:client
            Assert-That ($image.StatusCode -eq 200 -and ($image.Headers['Content-Type'] -join '') -eq 'image/png') 'Avatar unreadable'
            Assert-That (($image.Headers['Cache-Control'] -join ',') -match 'private' -and ($image.Headers['X-Content-Type-Options'] -join '') -eq 'nosniff') 'Avatar headers invalid'
        }
    }
    $token = (Api '/auth/csrf').data.token
    Api '/scenarios' 403 POST @{} $token | Out-Null
    function Sql($sql) {
        $sql | & "$MySqlBin/mysql.exe" --no-defaults --host=127.0.0.1 --port=13308 --user=root --batch --skip-column-names content_smoke
        Assert-That ($LASTEXITCODE -eq 0) 'SQL check failed'
    }
    try {
        Sql 'UPDATE scammer_profile SET is_enabled=0 WHERE id=1;'
        $disabled = Api '/scammer-profiles/1' 404
        $missing = Api '/scammer-profiles/999999' 404
        Assert-That ($disabled.code -eq $missing.code -and $disabled.message -eq $missing.message -and $null -eq $disabled.data) 'Disabled profile leaks existence'
        Assert-That ((Api '/scammer-profiles').data.items.Count -eq 1) 'Disabled profile still listed'
        Api ('/avatars/' + $s[0].avatar.key) 404 | Out-Null
    } finally { Sql 'UPDATE scammer_profile SET is_enabled=1 WHERE id=1;' }
    Api '/auth/logout' 200 POST $null $token | Out-Null
    $token = (Api '/auth/csrf').data.token
    Api '/auth/login' 200 POST @{username=$env:ADMIN_USERNAME;password=$env:ADMIN_PASSWORD} $token | Out-Null
    foreach ($path in $paths) { Api $path 403 | Out-Null }
    $tables = @(Sql "SELECT table_name FROM information_schema.tables WHERE table_schema='content_smoke' ORDER BY table_name;")
    Assert-That (($tables -join ',') -eq 'flyway_schema_history,fraud_scenario,scammer_profile,sys_user,victim_profile') 'Unexpected business tables'
    'PASS: MySQL 8.0.45 V1-V3 migrations, six content endpoints, pagination, avatars, role isolation and disabled profiles.' | Tee-Object "$runDir/result.txt"
} finally {
    if ($app -and -not $app.HasExited) { Stop-Process -Id $app.Id }
    if ($db -and -not $db.HasExited) {
        & "$MySqlBin/mysqladmin.exe" --no-defaults --host=127.0.0.1 --port=13308 --user=root shutdown 2>$null | Out-Null
        if ($LASTEXITCODE -ne 0) { Stop-Process -Id $db.Id }
    }
    foreach ($name in $envNames) { [Environment]::SetEnvironmentVariable($name, $saved[$name]) }
    Write-Output "Smoke logs: $runDir"
}
