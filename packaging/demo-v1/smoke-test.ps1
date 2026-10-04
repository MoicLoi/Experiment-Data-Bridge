[CmdletBinding()]param([string]$BaseUrl='http://127.0.0.1:25560',[string]$Username=$env:EDB_ADMIN_USER,[string]$Password=$env:EDB_ADMIN_PASSWORD)
$ErrorActionPreference='Stop'
$health=Invoke-RestMethod "$BaseUrl/api/v1/health" -TimeoutSec 5
if($health.status -ne 'UP'){throw 'Health endpoint did not report UP.'}
if([string]::IsNullOrWhiteSpace($Username)-or[string]::IsNullOrWhiteSpace($Password)){Write-Host 'Health passed; login smoke skipped because admin credentials were not supplied.' -ForegroundColor Yellow;exit 0}
$body=@{username=$Username;password=$Password}|ConvertTo-Json
$login=Invoke-RestMethod "$BaseUrl/api/v1/auth/login" -Method Post -ContentType 'application/json' -Body $body
if([string]::IsNullOrWhiteSpace($login.token)){throw 'Login did not return a token.'}
$headers=@{Authorization="Bearer $($login.token)"}
$null=Invoke-RestMethod "$BaseUrl/api/v1/user/me" -Headers $headers
$catalog=Invoke-RestMethod "$BaseUrl/api/v1/workflow-steps" -Headers $headers
if($null -eq $catalog.items){throw 'Step Catalog response is missing items.'}
Write-Host 'Smoke test passed: health, login, current user and Step Catalog.' -ForegroundColor Green
