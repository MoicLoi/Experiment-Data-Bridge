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
