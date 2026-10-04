$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$PidFile = Join-Path $Root '.edb-demo.pid'
if (!(Test-Path -LiteralPath $PidFile)) { Write-Host 'EDB Demo is not recorded as running.'; exit 0 }
$serverPid = [int](Get-Content -LiteralPath $PidFile -Raw)
$process = Get-Process -Id $serverPid -ErrorAction SilentlyContinue
if ($process) { Stop-Process -Id $serverPid; $process.WaitForExit(15000) }
Remove-Item -LiteralPath $PidFile -Force
Write-Host 'EDB Demo v1 stopped.' -ForegroundColor Green
