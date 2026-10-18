# Simple phase 3 smoke check against an isolated local MySQL 8.0.45 instance.
# Run with PowerShell 7 after building backend/target/anti-scam-backend-0.1.0.jar.
param([string]$MySqlBin = 'C:/Program Files/MySQL/MySQL Server 8.0/bin',
      [string]$Java = 'C:/Users/lenovo/.jdks/corretto-21.0.8/bin/java.exe')
$ErrorActionPreference = 'Stop'
$workspace = Split-Path $PSScriptRoot -Parent
$runDir = Join-Path $workspace ('.tools/admin-smoke-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $runDir | Out-Null
$db = $null; $app = $null
$envNames = @('MYSQL_PWD','DB_URL','DB_USERNAME','DB_PASSWORD','ADMIN_USERNAME','ADMIN_PASSWORD','AVATAR_STORE')
$saved = @{}
foreach ($name in $envNames) { $saved[$name] = [Environment]::GetEnvironmentVariable($name) }
function Assert-That($condition, $message) { if (-not $condition) { throw $message } }
try {
    foreach ($port in @(13309,18082)) {
        $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, $port)
        try { $listener.Start() } finally { $listener.Stop() }
    }
    [Environment]::SetEnvironmentVariable('MYSQL_PWD', $null)
    & "$MySqlBin/mysqld.exe" --no-defaults --initialize-insecure "--datadir=$runDir/data" 2> "$runDir/init.log"
    Assert-That ($LASTEXITCODE -eq 0) 'MySQL initialization failed'
    $db = Start-Process "$MySqlBin/mysqld.exe" -ArgumentList @('--no-defaults',"--datadir=$runDir/data",'--port=13309','--bind-address=127.0.0.1','--mysqlx=0','--character-set-server=utf8mb4','--collation-server=utf8mb4_0900_bin','--console') -WindowStyle Hidden -PassThru -RedirectStandardOutput "$runDir/mysql.out.log" -RedirectStandardError "$runDir/mysql.err.log"
    $ready = $false
    for ($i=0; $i -lt 60; $i++) {
        if ($db.HasExited) { throw 'Isolated MySQL exited; no database changes made' }
        & "$MySqlBin/mysqladmin.exe" --no-defaults --host=127.0.0.1 --port=13309 --user=root ping 2>$null | Out-Null
        if ($LASTEXITCODE -eq 0) { $ready = $true; break }
        Start-Sleep -Milliseconds 500
    }
    Assert-That $ready 'Temporary MySQL did not start'
    $env:DB_PASSWORD = [guid]::NewGuid().ToString('N')
    "ALTER USER 'root'@'localhost' IDENTIFIED BY '$env:DB_PASSWORD'; CREATE DATABASE admin_smoke CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;" | & "$MySqlBin/mysql.exe" --no-defaults --host=127.0.0.1 --port=13309 --user=root
    Assert-That ($LASTEXITCODE -eq 0) 'Database setup failed'
    $env:MYSQL_PWD = $env:DB_PASSWORD
    $env:DB_USERNAME = 'root'
    $env:DB_URL = 'jdbc:mysql://127.0.0.1:13309/admin_smoke?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true'
    $env:ADMIN_USERNAME = 'AdminSmoke'
    $env:ADMIN_PASSWORD = [guid]::NewGuid().ToString('N').Substring(0,24)
    $env:AVATAR_STORE = Join-Path $runDir 'assets'
    New-Item -ItemType Directory -Path (Join-Path $env:AVATAR_STORE 'avatars') | Out-Null
    Copy-Item -LiteralPath (Get-ChildItem "$workspace/backend/var/avatars" -Filter '*.png').FullName -Destination (Join-Path $env:AVATAR_STORE 'avatars')
    $app = Start-Process $Java -WorkingDirectory "$workspace/backend" -ArgumentList @('-jar',"$workspace/backend/target/anti-scam-backend-0.1.0.jar",'--spring.profiles.active=dev','--server.port=18082') -WindowStyle Hidden -PassThru -RedirectStandardOutput "$runDir/app.out.log" -RedirectStandardError "$runDir/app.err.log"
    $script:client = [Microsoft.PowerShell.Commands.WebRequestSession]::new()
    $ready = $false
    for ($i=0; $i -lt 90; $i++) {
        if ($app.HasExited) { throw "Backend exited; inspect $runDir/app.out.log" }
        try { Invoke-WebRequest 'http://127.0.0.1:18082/anti.scam/v1/auth/csrf' -WebSession $script:client | Out-Null; $ready=$true; break }
        catch { Start-Sleep -Milliseconds 500 }
    }
    Assert-That $ready 'Backend did not start'
    function Api($path, $status=200, $method='GET', $body=$null, $token=$null) {
        $options = @{Uri="http://127.0.0.1:18082/anti.scam/v1$path"; Method=$method; WebSession=$script:client; SkipHttpErrorCheck=$true}
        if ($null -ne $body) { $options.Body = [Text.Encoding]::UTF8.GetBytes(($body | ConvertTo-Json)); $options.ContentType='application/json;charset=UTF-8' }
        if ($token) { $options.Headers=@{'X-CSRF-TOKEN'=$token} }
        $response = Invoke-WebRequest @options
        Assert-That ($response.StatusCode -eq $status) "$method $path returned $($response.StatusCode), expected $status"
        Assert-That (($response.Headers['Cache-Control'] -join ',') -match 'no-store') 'Missing no-store'
        return ($response.Content | ConvertFrom-Json -DateKind String)
    }
    $token = (Api '/auth/csrf').data.token
    Api '/admin/scenarios' 401 | Out-Null
    Api '/auth/register' 201 POST @{username='AdminSmokeUser';password='123456';confirmPassword='123456'} $token | Out-Null
    Api '/auth/login' 200 POST @{username='AdminSmokeUser';password='123456'} $token | Out-Null
    $userClient = $script:client
    $token = (Api '/auth/csrf').data.token
    Api '/admin/scenarios' 403 | Out-Null
    Api '/admin/victim-profiles/1' 403 PUT @{} $token | Out-Null
    $script:client = [Microsoft.PowerShell.Commands.WebRequestSession]::new()
    $token = (Api '/auth/csrf').data.token
    Api '/auth/login' 200 POST @{username=$env:ADMIN_USERNAME;password=$env:ADMIN_PASSWORD} $token | Out-Null
    $adminClient = $script:client
    $token = (Api '/auth/csrf').data.token
    foreach ($path in @('/home','/scenarios','/avatars/missing','/simulations/1/report')) { Api $path 403 | Out-Null }
    $scenarios = (Api '/admin/scenarios').data
    Assert-That ($scenarios.Count -eq 3 -and $scenarios[0].version -eq 0) 'Scenario list invalid'
    Assert-That ($scenarios[0].createdAt -match '^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$') 'Timestamp format invalid'
    $copy = @{name='后台修改场景';description='更新后的简介';version=0}
    Api '/admin/scenarios/1' 403 PUT $copy | Out-Null
    $changed = (Api '/admin/scenarios/1' 200 PUT $copy $token).data
    Assert-That ($changed.version -eq 1 -and $changed.code -eq $scenarios[0].code -and $changed.focus -eq $scenarios[0].focus) 'Scenario update invalid'
    $conflict = Api '/admin/scenarios/1' 409 PUT $copy $token
    Assert-That ($conflict.code -eq 'VERSION_CONFLICT' -and $conflict.data.currentVersion -eq 1) 'Version conflict invalid'
    $copy.version=1
    Assert-That ((Api '/admin/scenarios/1' 200 PUT $copy $token).data.version -eq 2) 'Identical edit must increment version'
    $copy.version=2; $copy.code='TASK_REBATE'
    Assert-That ((Api '/admin/scenarios/1' 400 PUT $copy $token).data.fieldErrors[0].field -eq 'code') 'Unknown field not rejected'
    Write-Output 'PASS: permissions, CSRF, scenario editing and version conflicts'

    $scammers = (Api '/admin/scammer-profiles').data.items
    $victims = (Api '/admin/victim-profiles').data.items
    Assert-That ($scammers.Count -eq 2 -and $victims.Count -eq 3 -and $scammers[0].enabled) 'Initial profiles invalid'
    $page = (Api '/admin/scammer-profiles?limit=1').data
    $next = (Api ('/admin/scammer-profiles?limit=1&cursor=' + $page.nextCursor)).data
    Assert-That ($page.hasMore -and -not $next.hasMore -and $page.items[0].id -ne $next.items[0].id) 'Admin pagination invalid'
    Api ('/admin/victim-profiles?cursor=' + $page.nextCursor) 400 | Out-Null
    Api '/admin/victim-profiles?enabled=true' 400 | Out-Null

    $handler = [Net.Http.HttpClientHandler]::new()
    $handler.CookieContainer = $adminClient.Cookies
    $http = [Net.Http.HttpClient]::new($handler)
    try {
        $jobs = foreach ($p in $scammers) {
            $request = [Net.Http.HttpRequestMessage]::new([Net.Http.HttpMethod]::Post, "http://127.0.0.1:18082/anti.scam/v1/admin/scammer-profiles/$($p.id)/disable")
            $request.Headers.Add('X-CSRF-TOKEN', $token)
            $request.Content = [Net.Http.StringContent]::new('{"version":0}', [Text.Encoding]::UTF8, 'application/json')
            $http.SendAsync($request)
        }
        $responses = @($jobs | ForEach-Object { $_.GetAwaiter().GetResult() })
        Assert-That ((@($responses | Where-Object { [int]$_.StatusCode -eq 200 }).Count -eq 1) -and (@($responses | Where-Object { [int]$_.StatusCode -eq 409 }).Count -eq 1)) 'Concurrent disable did not preserve one enabled profile'
        foreach ($response in $responses) {
            if ([int]$response.StatusCode -eq 409) {
                Assert-That (($response.Content.ReadAsStringAsync().GetAwaiter().GetResult() | ConvertFrom-Json -DateKind String).code -eq 'LAST_ENABLED_PROFILE') 'Concurrent loser has incorrect error'
            }
            $response.Dispose()
        }
    } finally { $http.Dispose() }
    $after = (Api '/admin/scammer-profiles').data.items
    Assert-That (@($after | Where-Object enabled).Count -eq 1) 'No enabled scammer remains'
    $last = @($after | Where-Object enabled)[0]
    # D-1: permanent deletion was removed; an unmapped method answers 400 VALIDATION_FAILED and the record stays.
    Assert-That ((Api "/admin/scammer-profiles/$($last.id)" 400 DELETE @{version=$last.version} $token).code -eq 'VALIDATION_FAILED') 'DELETE still available'
    foreach ($p in $after) { if (-not $p.enabled) { Api "/admin/scammer-profiles/$($p.id)/enable" 200 POST @{version=$p.version} $token | Out-Null } }
    Write-Output 'PASS: pagination and concurrent last-enabled protection'

    function Upload($path, $status) {
        $response = Invoke-WebRequest 'http://127.0.0.1:18082/anti.scam/v1/admin/avatars' -Method Post -WebSession $adminClient -Headers @{'X-CSRF-TOKEN'=$token} -Form @{file=Get-Item -LiteralPath $path} -SkipHttpErrorCheck
        Assert-That ($response.StatusCode -eq $status) "Upload returned $($response.StatusCode), expected $status"
        Assert-That (($response.Headers['Cache-Control'] -join ',') -match 'no-store') 'Upload cache header missing'
        return ($response.Content | ConvertFrom-Json -DateKind String)
    }
    $png = (Get-ChildItem "$workspace/backend/var/avatars" -Filter '*.png' | Select-Object -First 1).FullName
    $uploaded = (Upload $png 201).data
    Assert-That ($uploaded.key -match '^[a-f0-9]{64}$' -and $uploaded.mediaType -eq 'image/png') 'Upload metadata invalid'
    $preview = Invoke-WebRequest ("http://127.0.0.1:18082" + $uploaded.previewUrl) -WebSession $adminClient
    Assert-That (($preview.Headers['Cache-Control'] -join ',') -match 'private' -and ($preview.Headers['X-Content-Type-Options'] -join '') -eq 'nosniff') 'Preview headers missing'
    $bytes = [IO.File]::ReadAllBytes($png)
    $padded = [byte[]]::new(5*1024*1024)
    [Array]::Copy($bytes, $padded, $bytes.Length)
    [IO.File]::WriteAllBytes("$runDir/exact.png", $padded)
    Upload "$runDir/exact.png" 201 | Out-Null
    [IO.File]::WriteAllBytes("$runDir/large.png", [byte[]]::new(5*1024*1024+1))
    Assert-That ((Upload "$runDir/large.png" 413).code -eq 'FILE_TOO_LARGE') 'Oversized upload error invalid'
    [IO.File]::WriteAllText("$runDir/fake.png", 'not an image')
    Assert-That ((Upload "$runDir/fake.png" 400).code -eq 'INVALID_IMAGE') 'Invalid image accepted'
    Write-Output 'PASS: upload, preview, invalid image and actual 5 MB boundary'

    foreach ($kind in @('scammer','victim')) {
        $seed = if ($kind -eq 'scammer') { $scammers[0] } else { $victims[0] }
        $body = @{}
        foreach ($property in $seed.PSObject.Properties) {
            if ($property.Name -notin @('id','avatar','speakingAvatar','enabled','version','createdAt','updatedAt')) { $body[$property.Name]=$property.Value }
        }
        $body.name = "新增$kind"; $body.avatarKey = $uploaded.key; $body.speakingAvatarKey = $uploaded.key
        $created = (Api "/admin/$kind-profiles" 201 POST $body $token).data
        Assert-That ($created.enabled -and $created.version -eq 0 -and $created.avatar.url -like '/anti.scam/v1/admin/avatars/*') 'Created profile invalid'
        $body.version=0; $body.name="修改$kind"
        $edited = (Api "/admin/$kind-profiles/$($created.id)" 200 PUT $body $token).data
        Assert-That ($edited.version -eq 1 -and $edited.name -eq $body.name) 'Profile update invalid'
        Assert-That ((Api "/admin/$kind-profiles/$($created.id)" 409 PUT $body $token).data.currentVersion -eq 1) 'Stale profile edit accepted'
        $body.version=1; $body.age='30'
        Assert-That ((Api "/admin/$kind-profiles/$($created.id)" 400 PUT $body $token).data.fieldErrors[0].field -eq 'age') 'String age accepted'
        $body.age=30.5
        Assert-That ((Api "/admin/$kind-profiles/$($created.id)" 400 PUT $body $token).data.fieldErrors[0].field -eq 'age') 'Fractional age accepted'
        $script:client=$userClient
        $userRows=(Api "/$kind-profiles").data.items
        Assert-That (@($userRows | Where-Object id -eq $created.id).Count -eq 1) 'New profile not visible to USER'
        Assert-That (-not ($userRows[0].PSObject.Properties.Name -contains 'version')) 'USER sees admin fields'
        $script:client=$adminClient
        $disabled=(Api "/admin/$kind-profiles/$($created.id)/disable" 200 POST @{version=1} $token).data
        Assert-That (-not $disabled.enabled -and $disabled.version -eq 2) 'Disable invalid'
        Assert-That ((Api "/admin/$kind-profiles/$($created.id)/disable" 200 POST @{version=0} $token).data.version -eq 2) 'Repeated disable increments version'
        $script:client=$userClient
        Api "/$kind-profiles/$($created.id)" 404 | Out-Null
        $script:client=$adminClient
        Assert-That (-not (Api "/admin/$kind-profiles/$($created.id)").data.enabled) 'Admin cannot read disabled profile'
        Api "/admin/$kind-profiles/$($created.id)/enable" 200 POST @{version=2} $token | Out-Null
        # D-1: no permanent deletion; the profile is still readable afterwards.
        Api "/admin/$kind-profiles/$($created.id)" 400 DELETE @{version=3} $token | Out-Null
        Assert-That ((Api "/admin/$kind-profiles/$($created.id)").data.id -eq $created.id) 'Profile disappeared after DELETE'
    }
    Write-Output 'PASS: both profile families create/edit/toggle, strict validation and USER visibility'
    $tables = "SELECT table_name FROM information_schema.tables WHERE table_schema='admin_smoke' ORDER BY table_name;" | & "$MySqlBin/mysql.exe" --no-defaults --host=127.0.0.1 --port=13309 --user=root --batch --skip-column-names
    Assert-That (($tables -join ',') -eq 'agent_prompt,flyway_schema_history,fraud_scenario,scammer_profile,simulation_message,simulation_report,simulation_round,simulation_session,sys_user,victim_profile') 'Unexpected schema change'
    'PASS: admin console smoke on isolated MySQL 8.0.45; no existing database touched.' | Tee-Object "$runDir/result.txt"

} finally {
    if ($app -and -not $app.HasExited) { Stop-Process -Id $app.Id }
    if ($db -and -not $db.HasExited) {
        & "$MySqlBin/mysqladmin.exe" --no-defaults --host=127.0.0.1 --port=13309 --user=root shutdown 2>$null | Out-Null
        if ($LASTEXITCODE -ne 0) { Stop-Process -Id $db.Id }
    }
    foreach ($name in $envNames) { [Environment]::SetEnvironmentVariable($name, $saved[$name]) }
    Write-Output "Smoke logs: $runDir"
}
