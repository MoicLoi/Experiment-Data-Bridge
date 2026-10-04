[CmdletBinding()]param([string]$Path = $PSScriptRoot)
$resolved = (Resolve-Path -LiteralPath $Path).Path
$highConfidencePatterns = @('sk-[A-Za-z0-9]{20,}','AKIA[0-9A-Z]{16}','-----BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY-----')
$configurationPatterns = @('password\s*:\s*(?!__|CHANGE_ME|replace-)[^\s#]{8,}','secret\s*:\s*(?!__|CHANGE_ME)[^\s#]{16,}')
$files = Get-ChildItem -LiteralPath $resolved -Recurse -File | Where-Object { $_.Extension -notin '.jar','.zip','.png','.jpg','.pdf','.xlsx' -and $_.Name -ne 'scan-secrets.ps1' }
$findings = foreach ($file in $files) {
  $patterns = @($highConfidencePatterns)
  if ($file.Extension -in '.yml','.yaml','.json','.properties','.conf','.toml','.env') { $patterns += $configurationPatterns }
  foreach ($pattern in $patterns) { Select-String -LiteralPath $file.FullName -Pattern $pattern -AllMatches | ForEach-Object { "$($file.FullName):$($_.LineNumber)" } }
}
if ($findings) { $findings | ForEach-Object { Write-Error "Potential secret: $_" }; throw 'Secret scan failed.' }
Write-Host "Secret scan passed: $($files.Count) text files checked." -ForegroundColor Green
