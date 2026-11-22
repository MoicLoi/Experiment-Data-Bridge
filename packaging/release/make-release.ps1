[CmdletBinding()]
param(
  # Directory produced by packaging/demo-v1/package-demo.ps1
  [string]$ReleaseDir = 'release\edb-demo-v1',
  # Where the archive, checksums and release notes are written
  [string]$OutputDir = 'dist',
  [string]$ArchiveBaseName
)

# Turns the verified demo package into a GitHub Release artifact set:
#
#   dist/edb-<version>-windows-x64.zip     the installable package
#   dist/SHA256SUMS.txt                    checksums for the archive and the fat JAR
#   dist/RELEASE_NOTES-<version>.md        notes to paste into the release
#
# Guards, because a release artifact is published to the world:
#   * the release directory must look like a complete package
#   * it must NOT contain a real config/application.yml (runtime credentials)
#   * the repository secret scanner must pass over it

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
Add-Type -AssemblyName System.IO.Compression
$Repo = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$Release = (Resolve-Path -LiteralPath (Join-Path $Repo $ReleaseDir)).Path
$Out = Join-Path $Repo $OutputDir
New-Item -ItemType Directory -Force -Path $Out | Out-Null

$Version = (Get-Content -LiteralPath (Join-Path $Release 'VERSION') -Raw).Trim()
if (!$Version) { throw "VERSION file is empty in $Release" }
if (!$ArchiveBaseName) { $ArchiveBaseName = "edb-$Version-windows-x64" }

Write-Host "Packaging $Version from $Release"

# --- Guard 1: package completeness -----------------------------------------
# eula.txt is deliberately NOT required: start.ps1 creates it on first run, so a
# freshly built package never contains it.
$required = @(
  'server\EXPERIMENT_DATA_BRIDGE.jar',
  'web\index.html',
  'cli\bin',
  'start.bat',
  'start.ps1',
  'stop.ps1',
  'config\application.yml.template',
  'VERSION'
)
foreach ($rel in $required) {
  if (!(Test-Path -LiteralPath (Join-Path $Release $rel))) {
    throw "Release directory is incomplete: missing $rel"
  }
}

# --- Guard 2: never ship runtime credentials -------------------------------
$runtimeConfig = Join-Path $Release 'config\application.yml'
if (Test-Path -LiteralPath $runtimeConfig) {
  throw "Refusing to package: $runtimeConfig exists and contains real runtime credentials. " +
        'Delete it (or rebuild the package) before publishing. The release must ship ' +
        'config/application.yml.template only.'
}

# --- Guard 3: secret scan --------------------------------------------------
$scanner = Join-Path $Repo 'packaging\demo-v1\scan-secrets.ps1'
if (Test-Path -LiteralPath $scanner) {
  Write-Host 'Running secret scan over the release directory...'
  & $scanner -Path $Release
  if ($LASTEXITCODE -and $LASTEXITCODE -ne 0) { throw 'Secret scan failed; refusing to package.' }
} else {
  Write-Warning "Secret scanner not found at $scanner; skipping."
}

# --- Archive ---------------------------------------------------------------
# Compress-Archive is deliberately NOT used: on Windows PowerShell 5.1 it writes
# ZIP entries with backslash separators, which is not the ZIP specification and
# makes unzip on Linux/macOS produce literal "cli\lib\x.jar" filenames. Entries
# are written by hand instead, with forward slashes and a single top-level folder.
$archive = Join-Path $Out "$ArchiveBaseName.zip"
if (Test-Path -LiteralPath $archive) { Remove-Item -LiteralPath $archive -Force }
Write-Host "Creating $archive ..."

$baseName = Split-Path $Release -Leaf
$files = Get-ChildItem -LiteralPath $Release -Recurse -File | Sort-Object FullName
$zipArchive = [IO.Compression.ZipFile]::Open($archive, [IO.Compression.ZipArchiveMode]::Create)
try {
  foreach ($file in $files) {
    $relative = $file.FullName.Substring($Release.Length + 1).Replace('\', '/')
    $entryName = "$baseName/$relative"
    [void][IO.Compression.ZipFileExtensions]::CreateEntryFromFile(
      $zipArchive, $file.FullName, $entryName, [IO.Compression.CompressionLevel]::Optimal)
  }
} finally { $zipArchive.Dispose() }
Write-Host ("  packed $($files.Count) files under $baseName/")

# --- Checksums -------------------------------------------------------------
$lines = @()
$zipHash = (Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash.ToLowerInvariant()
$lines += "$zipHash  $([IO.Path]::GetFileName($archive))"

# Also publish a checksum for the server JAR on its own, so users who only want
# the server can verify it without unpacking the archive.
$jarPath = Join-Path $Release 'server\EXPERIMENT_DATA_BRIDGE.jar'
$jarHash = (Get-FileHash -LiteralPath $jarPath -Algorithm SHA256).Hash.ToLowerInvariant()
$lines += "$jarHash  EXPERIMENT_DATA_BRIDGE.jar"

$sums = Join-Path $Out 'SHA256SUMS.txt'
[IO.File]::WriteAllLines($sums, $lines, [Text.UTF8Encoding]::new($false))

# --- Release notes ---------------------------------------------------------
$notesSource = Join-Path $Repo 'packaging\release\RELEASE_NOTES.md'
$notesTarget = Join-Path $Out "RELEASE_NOTES-$Version.md"
if (Test-Path -LiteralPath $notesSource) {
  $notes = [IO.File]::ReadAllText($notesSource, [Text.UTF8Encoding]::new($false))
  $notes = $notes.Replace('{{VERSION}}', $Version).Replace('{{ARCHIVE}}', [IO.Path]::GetFileName($archive))
  [IO.File]::WriteAllText($notesTarget, $notes, [Text.UTF8Encoding]::new($false))
  Write-Host "Wrote $notesTarget"
} else {
  Write-Warning "Release notes template not found at $notesSource"
}

$sizeMb = [math]::Round((Get-Item -LiteralPath $archive).Length / 1MB, 1)
Write-Host ''
Write-Host "Release artifacts ready in $Out" -ForegroundColor Green
Write-Host ("  {0}  ({1} MB)" -f [IO.Path]::GetFileName($archive), $sizeMb)
Write-Host '  SHA256SUMS.txt'
if (Test-Path -LiteralPath $notesTarget) { Write-Host ("  " + [IO.Path]::GetFileName($notesTarget)) }
$lines | ForEach-Object { Write-Host "    $_" }
