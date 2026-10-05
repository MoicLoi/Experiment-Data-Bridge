[CmdletBinding()]
param(
  # Directory of dependency JARs (for example the CLI distribution lib folder).
  [string]$LibDir,
  # A bundled/fat JAR whose META-INF/maven POM entries describe the server dependency set.
  [string]$ArchivePath,
  [Parameter(Mandatory = $true)][string]$OutputPath,
  [string]$Heading = 'EDB Third-Party Dependency Licenses'
)

# Collects license metadata from real shipped artifacts. Nothing is hand-written:
# every value is read from the artifact itself, so the document can be regenerated
# after any dependency change and cannot silently drift.
#
# Two complementary sources, because a release ships both:
#   -LibDir      one row per JAR in a distribution lib folder
#   -ArchivePath one row per dependency POM bundled inside a fat JAR
#
# Detection order per artifact, most reliable first:
#   1. Maven pom.xml  <license><name>         -> "pom.xml"
#   2. OSGi manifest  Bundle-License          -> "MANIFEST.MF"
#   3. Bundled LICENSE/NOTICE text signature  -> "license text"
#   4. nothing found                          -> UNKNOWN, needs manual review
#
# Usage:
#   pwsh -File packaging/demo-v1/collect-licenses.ps1 `
#     -LibDir release/edb-demo-v1/cli/lib `
#     -ArchivePath release/edb-demo-v1/server/EXPERIMENT_DATA_BRIDGE.jar `
#     -OutputPath THIRD-PARTY-LICENSES.md

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem

if (!$LibDir -and !$ArchivePath) { throw 'Provide at least one of -LibDir or -ArchivePath.' }
if ($LibDir -and !(Test-Path -LiteralPath $LibDir)) { throw "Library directory not found: $LibDir" }
if ($ArchivePath -and !(Test-Path -LiteralPath $ArchivePath)) { throw "Archive not found: $ArchivePath" }

# Signatures are matched against license text shipped inside an artifact.
$script:LicenseSignatures = @(
  @{ Name = 'Apache-2.0';   Test = { param($t) $t -match 'Apache License' -and $t -match 'Version 2\.0' } }
  # Must be tested before the generic BSD rule: EDL-1.0 is a BSD-3-Clause variant.
  @{ Name = 'EDL-1.0';      Test = { param($t) $t -match 'Eclipse Distribution License' } }
  @{ Name = 'EPL-2.0';      Test = { param($t) $t -match 'Eclipse Public License' -and $t -match '(v\.?\s*2\.0|Version 2\.0)' } }
  @{ Name = 'EPL-1.0';      Test = { param($t) $t -match 'Eclipse Public License' -and $t -match '(v\.?\s*1\.0|Version 1\.0)' } }
  @{ Name = 'MPL-2.0';      Test = { param($t) $t -match 'Mozilla Public License' -and $t -match '2\.0' } }
  @{ Name = 'GPL-2.0';      Test = { param($t) $t -match 'GNU General Public License' -and $t -match '(version 2|v\.?2)' } }
  @{ Name = 'GPL-3.0';      Test = { param($t) $t -match 'GNU General Public License' -and $t -match '(version 3|v\.?3)' } }
  @{ Name = 'LGPL-2.1';     Test = { param($t) $t -match 'GNU Lesser General Public License' } }
  @{ Name = 'BSD-3-Clause'; Test = { param($t) $t -match 'BSD' -and $t -match 'neither the name' } }
  @{ Name = 'BSD-2-Clause'; Test = { param($t) $t -match 'BSD' -and $t -match 'Redistribution and use' -and $t -notmatch 'neither the name' } }
  @{ Name = 'ISC';          Test = { param($t) $t -match 'ISC License' -or ($t -match 'Permission to use, copy, modify' -and $t -match 'with or without fee') } }
  @{ Name = 'MIT';          Test = { param($t) $t -match 'MIT License' -or $t -match 'Permission is hereby granted, free of charge' } }
  @{ Name = 'UPL-1.0';      Test = { param($t) $t -match 'Universal Permissive License' } }
  @{ Name = 'CC0-1.0';      Test = { param($t) $t -match 'CC0 1\.0' -or $t -match 'Creative Commons Legal Code' } }
  @{ Name = 'Public-Domain';Test = { param($t) $t -match 'released into the public domain' } }
)

# Vendors publish the same license in many shapes: a URL, "The Apache Software
# License, Version 2.0", "Apache 2", ... Normalise to an SPDX-style identifier so
# the inventory stays readable and groupable.
function ConvertTo-SpdxName {
  param([string]$Raw)
  $value = ($Raw -replace '\s+', ' ').Trim()
  $probe = $value.ToLowerInvariant()
  switch -Regex ($probe) {
    'apache\.org/licenses/license-2\.0|apache software license,?\s*version 2\.0|apache license,?\s*version 2\.0|^apache[- ]?2(\.0)?$' { return 'Apache-2.0' }
    'opensource\.org/license/mit|opensource\.org/licenses/mit|^mit( license)?$' { return 'MIT' }
    'opensource\.org/licenses/bsd-3-clause|^bsd-3-clause$|^bsd 3-clause' { return 'BSD-3-Clause' }
    'opensource\.org/licenses/bsd-2-clause|^bsd-2-clause$' { return 'BSD-2-Clause' }
    'opensource\.org/licenses/isc|^isc$' { return 'ISC' }
    'eclipse public license.*2\.0|^epl-2\.0$' { return 'EPL-2.0' }
    'eclipse public license.*1\.0|^epl-1\.0$' { return 'EPL-1.0' }
    'eclipse distribution license.*1\.0|^edl-1\.0$' { return 'EDL-1.0' }
    'mozilla public license.*2\.0|^mpl-2\.0$' { return 'MPL-2.0' }
    'gnu (lesser )?general public license' { return $value }
    'public domain' { return 'Public-Domain' }
    '^the bsd license$' { return 'BSD (clause count not stated)' }
  }
  return $value
}

function Read-ZipEntryText {
  param($Entry)
  $reader = New-Object IO.StreamReader($Entry.Open())
  try { return $reader.ReadToEnd() } finally { $reader.Dispose() }
}

function Get-PomLicenseNames {
  param([string]$PomXml)
  $names = @()
  foreach ($m in [regex]::Matches($PomXml, '(?s)<license>(.*?)</license>')) {
    $nameMatch = [regex]::Match($m.Groups[1].Value, '(?s)<name>\s*([^<]+?)\s*</name>')
    if ($nameMatch.Success) {
      $name = ConvertTo-SpdxName $nameMatch.Groups[1].Value
      if ($name -and $name -notin $names) { $names += $name }
    }
  }
  if ($names.Count -eq 0) {
    foreach ($m in [regex]::Matches($PomXml, '(?s)<licenseUrl>\s*([^<]+?)\s*</licenseUrl>')) {
      $name = ConvertTo-SpdxName $m.Groups[1].Value
      if ($name -and $name -notin $names) { $names += $name }
    }
  }
  return $names
}

function Get-LibDirRows {
  param([string]$Directory)

  $jars = Get-ChildItem -LiteralPath $Directory -Filter *.jar -File | Sort-Object Name
  $rows = foreach ($jar in $jars) {
    $licenses = @(); $sources = @(); $coordinates = ''
    $zip = [IO.Compression.ZipFile]::OpenRead($jar.FullName)
    try {
      $jarBase = [IO.Path]::GetFileNameWithoutExtension($jar.Name)
      $candidates = @()
      foreach ($entry in ($zip.Entries | Where-Object { $_.FullName -match '^META-INF/maven/.+/pom\.xml$' })) {
        $pom = Read-ZipEntryText $entry
        foreach ($name in (Get-PomLicenseNames $pom)) { if ($name -notin $licenses) { $licenses += $name; $sources += 'pom.xml' } }
        $g = [regex]::Match($pom, '(?s)<groupId>\s*([^<]+?)\s*</groupId>')
        $a = [regex]::Match($pom, '(?s)<artifactId>\s*([^<]+?)\s*</artifactId>')
        $v = [regex]::Match($pom, '(?s)<version>\s*([^<]+?)\s*</version>')
        if ($a.Success) {
          $candidates += [pscustomobject]@{
            ArtifactId  = $a.Groups[1].Value.Trim()
            Coordinates = (@($g.Groups[1].Value.Trim(), $a.Groups[1].Value.Trim(), $v.Groups[1].Value.Trim()) | Where-Object { $_ }) -join ':'
          }
        }
      }
      # A JAR can carry both its own POM and a parent POM; prefer the artifact
      # whose id matches the JAR file name.
      $exact = $candidates | Where-Object { $_.ArtifactId -eq $jarBase } | Select-Object -First 1
      $loose = $candidates | Where-Object { $jarBase -like "$($_.ArtifactId)*" } | Select-Object -First 1
      if ($exact) { $coordinates = $exact.Coordinates }
      elseif ($loose) { $coordinates = $loose.Coordinates }
      elseif ($candidates.Count) { $coordinates = $candidates[0].Coordinates }
      if ($licenses.Count -eq 0) {
        $manifest = $zip.Entries | Where-Object { $_.FullName -eq 'META-INF/MANIFEST.MF' } | Select-Object -First 1
        if ($manifest) {
          $mf = Read-ZipEntryText $manifest
          $m = [regex]::Match($mf, '(?m)^Bundle-License\s*:\s*(.+)$')
          if ($m.Success) { $licenses += (ConvertTo-SpdxName $m.Groups[1].Value); $sources += 'MANIFEST.MF' }
        }
      }
      if ($licenses.Count -eq 0) {
        $textEntries = $zip.Entries | Where-Object { $_.FullName -match '(?i)^(META-INF/)?(LICENSE|LICENCE|COPYING|NOTICE|ABOUT)(\.txt|\.md|\.html)?$' }
        $combined = ($textEntries | Select-Object -First 4 | ForEach-Object { Read-ZipEntryText $_ }) -join "`n"
        if ($combined) {
          $flat = $combined -replace '\s+', ' '
          # Collect EVERY signature that matches. Artifacts such as the MySQL
          # Connector/J licensing manual bundle several licenses in one file, and
          # reporting only the first hit would be false precision.
          $hits = @()
          foreach ($sig in $script:LicenseSignatures) {
            if (& $sig.Test $flat) { $hits += $sig.Name }
          }
          if ($hits.Count -eq 1) { $licenses += $hits[0]; $sources += 'license text' }
          elseif ($hits.Count -gt 1) { $licenses += "Multiple - see bundled license text ($($hits -join ', '))"; $sources += 'license text' }
        }
      }
    } finally { $zip.Dispose() }
    if ($licenses.Count -eq 0) { $licenses = @('UNKNOWN'); $sources = @('-') }
    [pscustomobject]@{
      Artifact    = $jar.Name
      Coordinates = $coordinates
      License     = ($licenses -join '; ')
      Source      = (($sources | Select-Object -Unique) -join ', ')
    }
  }
  return @($rows)
}

function Get-ArchiveRows {
  param([string]$Path)

  $zip = [IO.Compression.ZipFile]::OpenRead((Resolve-Path -LiteralPath $Path))
  try {
    $rows = foreach ($entry in ($zip.Entries | Where-Object { $_.FullName -match '^META-INF/maven/.+/pom\.xml$' } | Sort-Object FullName)) {
      $pom = Read-ZipEntryText $entry
      $parts = $entry.FullName -split '/'
      $g = $parts[2]; $a = $parts[3]
      $v = [regex]::Match($pom, '(?s)<version>\s*([^<]+?)\s*</version>').Groups[1].Value
      $licenses = @(Get-PomLicenseNames $pom)
      if ($licenses.Count -eq 0) { $licenses = @('UNKNOWN') }
      [pscustomobject]@{
        Module      = "$g`:$a"
        Version     = $v
        License     = ($licenses -join '; ')
        Source      = if ($licenses -contains 'UNKNOWN') { '-' } else { 'bundled pom.xml' }
      }
    }
  } finally { $zip.Dispose() }
  return @($rows)
}

$libRows = if ($LibDir) { Get-LibDirRows -Directory $LibDir } else { @() }
$archiveRows = if ($ArchivePath) { Get-ArchiveRows -Path $ArchivePath } else { @() }

if ($LibDir) { Write-Host "Inspecting $($libRows.Count) JAR(s) under $LibDir" }
if ($ArchivePath) { Write-Host "Inspecting $($archiveRows.Count) bundled dependency POM(s) in $ArchivePath" }

$generated = (Get-Date).ToUniversalTime().ToString('yyyy-MM-dd HH:mm:ss')
$libUnknown = @($libRows | Where-Object { $_.License -eq 'UNKNOWN' })
$archiveUnknown = @($archiveRows | Where-Object { $_.License -eq 'UNKNOWN' })

$lines = @()
$lines += "# $Heading"
$lines += ''
$lines += 'Generated from the dependency artifacts shipped in a release package; this file is'
$lines += '**not maintained by hand**. Regenerate it after any dependency change:'
$lines += ''
$lines += '```powershell'
$lines += 'pwsh -File packaging/demo-v1/collect-licenses.ps1 `'
$lines += '  -LibDir release/edb-demo-v1/cli/lib `'
$lines += '  -ArchivePath release/edb-demo-v1/server/EXPERIMENT_DATA_BRIDGE.jar `'
$lines += '  -OutputPath THIRD-PARTY-LICENSES.md'
$lines += '```'
$lines += ''
$lines += "- Generated (UTC): $generated"
if ($LibDir) { $lines += "- CLI distribution JARs inspected: $($libRows.Count) (unresolved: $($libUnknown.Count))" }
if ($ArchivePath) { $lines += "- Server fat JAR bundled POMs inspected: $($archiveRows.Count) (unresolved: $($archiveUnknown.Count))" }
$lines += ''
$lines += '## How each value was obtained'
$lines += ''
$lines += '| ``Source`` | Meaning |'
$lines += '|---|---|'
$lines += '| ``bundled pom.xml`` / ``pom.xml`` | Vendor-declared license in Maven metadata - most authoritative. |'
$lines += '| ``MANIFEST.MF`` | ``Bundle-License`` from the OSGi manifest. |'
$lines += '| ``license text`` | Signature detected in the license text bundled in the JAR. |'
$lines += '| ``-`` | Nothing machine-readable found - open the artifact and verify by hand. |'
$lines += ''
$lines += 'Names are normalised to SPDX-style identifiers. A license name here is what an'
$lines += 'artifact declares about itself: this document is an engineering inventory,'
$lines += '**not legal advice**.'
$lines += ''

if ($archiveRows.Count -gt 0) {
  $lines += '## Server dependencies (from the fat JAR)'
  $lines += ''
  $lines += '| Module | Version | Declared license | Source |'
  $lines += '|---|---|---|---|'
  foreach ($row in $archiveRows) { $lines += "| ``$($row.Module)`` | $($row.Version) | $($row.License) | $($row.Source) |" }
  $lines += ''
}

if ($libRows.Count -gt 0) {
  $lines += '## CLI distribution JARs'
  $lines += ''
  $lines += '| Artifact | Maven coordinates | Declared license | Source |'
  $lines += '|---|---|---|---|'
  foreach ($row in $libRows) {
    $coords = if ($row.Coordinates) { "``$($row.Coordinates)``" } else { '-' }
    $lines += "| ``$($row.Artifact)`` | $coords | $($row.License) | $($row.Source) |"
  }
  $lines += ''
}

$unresolved = @($archiveUnknown | ForEach-Object { "$($_.Module) $($_.Version)" }) + @($libUnknown | ForEach-Object { $_.Artifact })
if ($unresolved.Count -gt 0) {
  $lines += '## Unresolved before publishing binaries'
  $lines += ''
  $lines += 'These artifacts ship no machine-readable license metadata and must be verified by hand:'
  $lines += ''
  foreach ($item in $unresolved) { $lines += "- ``$item``" }
  $lines += ''
}

[IO.File]::WriteAllLines($OutputPath, $lines, [Text.UTF8Encoding]::new($false))
Write-Host "Wrote $OutputPath" -ForegroundColor Green
Write-Host "  unresolved: $($unresolved.Count) of $($archiveRows.Count + $libRows.Count)" -ForegroundColor $(if ($unresolved.Count) { 'Yellow' } else { 'Green' })
