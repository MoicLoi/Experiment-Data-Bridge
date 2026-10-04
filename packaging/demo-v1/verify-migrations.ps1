[CmdletBinding()]param([string]$SourceRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')))
$files = @(Get-ChildItem -Path (Join-Path $SourceRoot 'core\src\main\resources\db\migration'),(Join-Path $SourceRoot 'agent\src\main\resources\db\migration') -Filter 'V*__*.sql')
if (!$files) { throw 'No Flyway migrations were found.' }
$items = $files | ForEach-Object { if ($_.Name -notmatch '^V(\d+)__[A-Za-z0-9_]+\.sql$') { throw "Invalid migration name: $($_.Name)" }; [pscustomobject]@{ Version=[int]$Matches[1]; Name=$_.Name } }
$duplicates = $items | Group-Object Version | Where-Object Count -gt 1
if ($duplicates) { throw "Duplicate Flyway versions: $($duplicates.Name -join ', ')" }
if (($items | Measure-Object Version -Minimum).Minimum -ne 1) { throw 'Flyway history must start at V1.' }
$latest = ($items | Measure-Object Version -Maximum).Maximum
Write-Host "Flyway migration check passed: $($items.Count) files, V1..V$latest, no duplicate versions." -ForegroundColor Green
