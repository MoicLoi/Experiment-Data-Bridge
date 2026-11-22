[CmdletBinding()]param(
  [switch]$SkipBuild,
  [ValidatePattern('^[a-z0-9][a-z0-9.-]{2,63}$')][string]$ReleaseName='edb-demo-v1',
  [ValidatePattern('^[0-9A-Za-z][0-9A-Za-z.+-]{2,63}$')][string]$VersionLabel='0.25.0-demo.1'
)
$ErrorActionPreference='Stop'
$Repo=(Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$ReleaseRoot=Join-Path $Repo 'release'
$Target=Join-Path $ReleaseRoot $ReleaseName
if(!$Target.StartsWith($ReleaseRoot,[StringComparison]::OrdinalIgnoreCase)){throw 'Release target escaped the release directory.'}
$pidFile=Join-Path $Target '.edb-demo.pid'
if(Test-Path -LiteralPath $pidFile){
  $runningPid=(Get-Content -LiteralPath $pidFile -Raw).Trim()
  if($runningPid -match '^\d+$' -and (Get-Process -Id ([int]$runningPid) -ErrorAction SilentlyContinue)){
    throw "EDB Demo is running with PID $runningPid. Stop it before packaging; no release files were changed."
  }
}
if(!$SkipBuild){
  Push-Location (Join-Path $Repo 'web-ui');try{& npm.cmd run typecheck;if($LASTEXITCODE){throw 'Web typecheck failed.'};& npm.cmd run build;if($LASTEXITCODE){throw 'Web build failed.'}}finally{Pop-Location}
  Push-Location $Repo;try{& .\gradlew.bat :web:buildFatJar :cli:installDist;if($LASTEXITCODE){throw 'Gradle release build failed.'}}finally{Pop-Location}
}
$jar=Join-Path $Repo 'web\build\libs\EXPERIMENT_DATA_BRIDGE.jar';if(!(Test-Path -LiteralPath $jar)){throw "Fat JAR missing: $jar"}
$jarEntries = @(& jar tf $jar)
if ($LASTEXITCODE -ne 0) { throw 'Unable to inspect the Fat JAR. Ensure the JDK jar tool is available.' }
if ('META-INF/services/org.flywaydb.core.extensibility.Plugin' -notin $jarEntries) {
  throw 'Fat JAR is missing the merged Flyway ServiceLoader descriptor. Check shadowJar.mergeServiceFiles().'
}
$web=Join-Path $Repo 'web-ui\dist';if(!(Test-Path -LiteralPath $web)){throw "Web build missing: $web"}
$cli=Join-Path $Repo 'cli\build\install\cli';if(!(Test-Path -LiteralPath $cli)){throw "CLI distribution missing: $cli"}
New-Item -ItemType Directory -Force -Path $ReleaseRoot|Out-Null
if(Test-Path -LiteralPath $Target){Remove-Item -LiteralPath $Target -Recurse -Force}
@('server','web','cli','config\workflows','config\workflow-templates','config\reports','plugins','data','logs','examples\data')|ForEach-Object{New-Item -ItemType Directory -Force -Path (Join-Path $Target $_)|Out-Null}
Copy-Item -LiteralPath $jar -Destination (Join-Path $Target 'server')
Copy-Item -Path (Join-Path $web '*') -Destination (Join-Path $Target 'web') -Recurse
Copy-Item -Path (Join-Path $cli '*') -Destination (Join-Path $Target 'cli') -Recurse

# --- CLI dependency slimming ------------------------------------------------
# The cli module depends on :core, so its installDist drags core's whole
# transitive dependency graph (POI, PDFBox, MySQL, Flyway, Redis, Docker, JGit…)
# into the package - 101 JARs / ~72 MB that are already inside the server fat
# JAR. Only the CLI framework itself is unique to the CLI.
#
# So: keep the CLI-specific JARs, point the launchers at the server fat JAR, and
# prove the result still runs before declaring the package good.
$cliLib = Join-Path $Target 'cli\lib'
$serverJar = Join-Path $Target 'server\EXPERIMENT_DATA_BRIDGE.jar'
if (!(Test-Path -LiteralPath $serverJar)) { throw 'CLI slimming requires the server fat JAR, which is missing.' }

$cliKeepPattern = '^(cli-|clikt|mordant|colormath|jansi|jna)'
$cliJars = @(Get-ChildItem -LiteralPath $cliLib -Filter *.jar -File)
$toRemove = @($cliJars | Where-Object { $_.Name -notmatch $cliKeepPattern })
$reclaimedBytes = ($toRemove | Measure-Object Length -Sum).Sum
$toRemove | Remove-Item -Force
Write-Host ("CLI slimmed: kept {0} JAR(s), removed {1} duplicate JAR(s), reclaimed {2:N1} MB" -f ($cliJars.Count - $toRemove.Count), $toRemove.Count, ($reclaimedBytes / 1MB))

# Rewrite the classpath line in both launchers. Line endings are preserved by
# splitting on them, so the bash script keeps LF and the .bat keeps CRLF.
foreach ($launcher in @(@{ Path = (Join-Path $Target 'cli\bin\cli.bat'); Prefix = 'set CLASSPATH='; Line = 'set CLASSPATH=%APP_HOME%\lib\*;%APP_HOME%\..\server\EXPERIMENT_DATA_BRIDGE.jar' },
                        @{ Path = (Join-Path $Target 'cli\bin\cli');     Prefix = 'CLASSPATH=';     Line = 'CLASSPATH=$APP_HOME/lib/*:$APP_HOME/../server/EXPERIMENT_DATA_BRIDGE.jar' })) {
  $path = $launcher.Path
  if (!(Test-Path -LiteralPath $path)) { continue }
  $encoding = [Text.UTF8Encoding]::new($false)
  $parts = [regex]::Split([IO.File]::ReadAllText($path, $encoding), '(\r?\n)')
  $patched = $false
  for ($i = 0; $i -lt $parts.Count; $i++) {
    if ($parts[$i].StartsWith($launcher.Prefix)) { $parts[$i] = $launcher.Line; $patched = $true; break }
  }
  if (!$patched) { throw "Could not rewrite the classpath in $path" }
  [IO.File]::WriteAllText($path, ($parts -join ''), $encoding)
}
Write-Host 'CLI launchers repointed at the server fat JAR.'

# Smoke-test the slimmed CLI: a silently broken CLI is worse than a big package.
$cliClasspath = (Join-Path $cliLib '*') + ';' + $serverJar
$cliOutput = & java -cp $cliClasspath me.moiclathy.edb.cli.CliMainKt --help 2>&1 | Out-String
if ($LASTEXITCODE -ne 0 -or $cliOutput -notmatch 'Usage: edb') {
  throw "Slimmed CLI smoke test failed (exit $LASTEXITCODE). Output:`n$cliOutput"
}
Write-Host 'CLI smoke test passed (wrapper --help).'

Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'config\application.yml.template') -Destination (Join-Path $Target 'config')
Copy-Item -Path (Join-Path $PSScriptRoot 'examples\workflows\*') -Destination (Join-Path $Target 'config\workflows')
Copy-Item -Path (Join-Path $PSScriptRoot 'examples\workflow-templates\*') -Destination (Join-Path $Target 'config\workflow-templates')
Copy-Item -Path (Join-Path $PSScriptRoot 'examples\reports\*') -Destination (Join-Path $Target 'config\reports')
Copy-Item -Path (Join-Path $PSScriptRoot 'examples\data\*') -Destination (Join-Path $Target 'examples\data')
@('start.bat','start.ps1','stop.ps1','smoke-test.ps1','verify-migrations.ps1','scan-secrets.ps1','.env.example','VERSION','DEMO.md')|ForEach-Object{Copy-Item -LiteralPath (Join-Path $PSScriptRoot $_) -Destination $Target}
[IO.File]::WriteAllText((Join-Path $Target 'VERSION'),$VersionLabel+"`r`n",[Text.UTF8Encoding]::new($false))
Copy-Item -LiteralPath (Join-Path $Repo 'develop.md') -Destination $Target
Copy-Item -LiteralPath (Join-Path $Repo 'frontend-manual-test.md') -Destination $Target
& (Join-Path $PSScriptRoot 'verify-migrations.ps1') -SourceRoot $Repo
& (Join-Path $PSScriptRoot 'scan-secrets.ps1') -Path $Target
$checksumFile=Join-Path $Target 'SHA256SUMS';$lines=Get-ChildItem -LiteralPath $Target -Recurse -File|Where-Object{$_.FullName-ne$checksumFile}|Sort-Object FullName|ForEach-Object{$hash=Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256;$relative=$_.FullName.Substring($Target.Length+1).Replace('\','/');"$($hash.Hash.ToLowerInvariant())  $relative"};[IO.File]::WriteAllLines($checksumFile,$lines,[Text.UTF8Encoding]::new($false))
Write-Host "Demo package created: $Target" -ForegroundColor Green
