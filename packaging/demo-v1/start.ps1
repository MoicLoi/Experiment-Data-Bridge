[CmdletBinding()]
param(
    [switch]$NoBrowser,
    [switch]$SkipHealthCheck
)

$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$ConfigDir = Join-Path $Root 'config'
$Template = Join-Path $ConfigDir 'application.yml.template'
$Config = Join-Path $ConfigDir 'application.yml'
$Eula = Join-Path $Root 'eula.txt'
$Jar = Join-Path $Root 'server\EXPERIMENT_DATA_BRIDGE.jar'
$LogDir = Join-Path $Root 'logs'
$OutLog = Join-Path $LogDir 'server.out.log'
$ErrorLog = Join-Path $LogDir 'server.err.log'
$LauncherLog = Join-Path $LogDir 'launcher.log'
$PidFile = Join-Path $Root '.edb-demo.pid'
$ServerUrl = 'http://127.0.0.1:25560'
$Version = if (Test-Path (Join-Path $Root 'VERSION')) { (Get-Content (Join-Path $Root 'VERSION') -Raw).Trim() } else { '1.0.0' }

trap {
    New-Item -ItemType Directory -Force -Path $LogDir | Out-Null
    $position = $_.InvocationInfo.PositionMessage
    $record = "[{0:O}] {1}`r`n{2}`r`n" -f (Get-Date), $_.Exception.Message, $position
    Add-Content -LiteralPath $LauncherLog -Value $record -Encoding UTF8
    $lineCount = @(Get-Content -LiteralPath $LauncherLog).Count
    Write-Host "Launcher error recorded in logs/launcher.log around line $([Math]::Max(1, $lineCount - 3))." -ForegroundColor Red
    exit 1
}

function Write-Stage([string]$Message) {
    Write-Host $Message -ForegroundColor Cyan
}

function New-RandomSecret {
    $bytes = New-Object byte[] 64
    $random = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $random.GetBytes($bytes) } finally { $random.Dispose() }
    return [Convert]::ToBase64String($bytes)
}

function As-YamlString([string]$Value) {
    return ($Value | ConvertTo-Json -Compress)
}

function Initialize-ServerFiles {
    New-Item -ItemType Directory -Force -Path $ConfigDir, $LogDir | Out-Null
    if (!(Test-Path -LiteralPath $Template)) { throw "Configuration template not found: $Template" }

    $created = $false
    if (!(Test-Path -LiteralPath $Eula)) {
        @'
# By changing eula=false to eula=true you indicate that you accept the EDB Demo v1 EULA.
# Review DEMO.md and develop.md before enabling a production-like deployment.
eula=false
'@ | Set-Content -LiteralPath $Eula -Encoding UTF8
        $created = $true
    }

    if (!(Test-Path -LiteralPath $Config)) {
        $document = Get-Content -LiteralPath $Template -Raw
        $values = @{
            '__DB_HOST__' = As-YamlString '127.0.0.1'
            '__DB_PORT__' = '3306'
            '__DB_NAME__' = As-YamlString 'edb'
            '__DB_USER__' = As-YamlString 'edb'
            '__DB_PASSWORD__' = As-YamlString 'CHANGE_ME_DATABASE_PASSWORD'
            '__JWT_SECRET__' = As-YamlString (New-RandomSecret)
            '__ADMIN_USER__' = As-YamlString 'admin'
            '__ADMIN_PASSWORD__' = As-YamlString 'CHANGE_ME_ADMIN_PASSWORD'
        }
        foreach ($entry in $values.GetEnumerator()) {
            $document = $document.Replace($entry.Key, [string]$entry.Value)
        }
        [IO.File]::WriteAllText($Config, $document, [Text.UTF8Encoding]::new($false))
        $created = $true
    }

    if ($created) {
        Write-Host 'First-run files have been generated:' -ForegroundColor Yellow
        Write-Host "  $Eula"
        Write-Host "  $Config"
        Write-Host 'Set eula=true and replace every CHANGE_ME value, then run start.bat again.' -ForegroundColor Yellow
        return $true
    }
    return $false
}

function Assert-ServerConfiguration {
    $eulaAccepted = Select-String -LiteralPath $Eula -Pattern '^\s*eula\s*=\s*true\s*$' -Quiet
    if (!$eulaAccepted) { throw 'EULA is not accepted. Set eula=true in eula.txt.' }

    $unsafe = Select-String -LiteralPath $Config -Pattern 'CHANGE_ME|__[A-Z0-9_]+__' -AllMatches
    if ($unsafe) { throw 'Configuration contains unresolved CHANGE_ME or template values. Edit config/application.yml.' }
}

function Get-ErrorLocation {
    if (!(Test-Path -LiteralPath $ErrorLog)) { return "No error log was created." }
    $lines = @(Get-Content -LiteralPath $ErrorLog)
    $line = [Math]::Max(1, $lines.Count - 20)
    return "See logs/server.err.log around line $line (total $($lines.Count) lines)."
}

if (Initialize-ServerFiles) { exit 0 }
Assert-ServerConfiguration
if (!(Test-Path -LiteralPath $Jar)) { throw "Server JAR not found: $Jar" }
New-Item -ItemType Directory -Force -Path $LogDir | Out-Null

if (Test-Path -LiteralPath $PidFile) {
    $oldPid = (Get-Content -LiteralPath $PidFile -Raw).Trim()
    if ($oldPid -match '^\d+$' -and (Get-Process -Id ([int]$oldPid) -ErrorAction SilentlyContinue)) {
        throw "EDB is already running with PID $oldPid."
    }
    Remove-Item -LiteralPath $PidFile -Force
}

$startedAt = Get-Date
Write-Host "EDB Server v$Version starting..." -ForegroundColor Green
Write-Stage 'Loading configuration...'
Write-Stage 'Loading modules: core, web, agent, cli, plugin-api...'
Write-Stage 'Connecting to database...'
Write-Stage 'Starting HTTP server on port 25560...'
Write-Stage 'Starting Agent service...'
Write-Stage 'Loading plugins...'

# Clear logs so reported line numbers always refer to this run.
[IO.File]::WriteAllText($OutLog, '', [Text.UTF8Encoding]::new($false))
[IO.File]::WriteAllText($ErrorLog, '', [Text.UTF8Encoding]::new($false))

try {
    $process = Start-Process -FilePath 'java' -ArgumentList @('-jar', ('"{0}"' -f $Jar)) `
        -WorkingDirectory $Root -RedirectStandardOutput $OutLog -RedirectStandardError $ErrorLog `
        -NoNewWindow -PassThru
    [IO.File]::WriteAllText($PidFile, [string]$process.Id, [Text.UTF8Encoding]::new($false))

    $ready = $SkipHealthCheck.IsPresent
    if (!$ready) {
        for ($attempt = 0; $attempt -lt 120 -and !$ready -and !$process.HasExited; $attempt++) {
            Start-Sleep -Milliseconds 500
            try {
                $response = Invoke-RestMethod "$ServerUrl/api/v1/health" -TimeoutSec 2
                $ready = $response.status -eq 'UP'
            } catch { }
        }
    }

    if (!$ready) {
        if (!$process.HasExited) { Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue }
        throw "EDB did not become healthy. $(Get-ErrorLocation)"
    }

    $elapsed = ((Get-Date) - $startedAt).TotalSeconds
    Write-Host ("Done!({0:N1}s)! For help, type `"edb help`"" -f $elapsed) -ForegroundColor Green
    Write-Host "Web console: $ServerUrl"
    if (!$NoBrowser) { Start-Process $ServerUrl }

    $highCpuSamples = 0
    while (!$process.HasExited) {
        $beforeCpu = $process.TotalProcessorTime.TotalMilliseconds
        $sampleStarted = Get-Date
        Start-Sleep -Seconds 10
        $process.Refresh()
        if ($process.HasExited) { break }

        $sampleMs = [Math]::Max(1, ((Get-Date) - $sampleStarted).TotalMilliseconds)
        $cpuPercent = (($process.TotalProcessorTime.TotalMilliseconds - $beforeCpu) / $sampleMs / [Environment]::ProcessorCount) * 100
        $highCpuSamples = if ($cpuPercent -ge 90) { $highCpuSamples + 1 } else { 0 }

        $latency = [Diagnostics.Stopwatch]::StartNew()
        try { Invoke-RestMethod "$ServerUrl/api/v1/health" -TimeoutSec 5 | Out-Null } catch { }
        $latency.Stop()
        if ($latency.ElapsedMilliseconds -ge 2000 -or $highCpuSamples -ge 3) {
            Write-Warning ("Can't Keep Up! Is the server overloaded? health={0}ms cpu={1:N0}%" -f $latency.ElapsedMilliseconds, $cpuPercent)
            $highCpuSamples = 0
        }
    }

    if ($process.ExitCode -ne 0) {
        throw "EDB exited with code $($process.ExitCode). $(Get-ErrorLocation)"
    }
} catch {
    Write-Host "Startup failed: $($_.Exception.Message)" -ForegroundColor Red
    throw
} finally {
    if (Test-Path -LiteralPath $PidFile) { Remove-Item -LiteralPath $PidFile -Force }
}
