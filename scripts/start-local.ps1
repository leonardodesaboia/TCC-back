param(
    [string]$FrontendPath = (Join-Path $PSScriptRoot '../../TCC-front'),
    [string]$EnvironmentOverride
)
$ErrorActionPreference = 'Stop'
$backend = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$frontend = (Resolve-Path -LiteralPath $FrontendPath).Path
if (-not (Test-Path (Join-Path $frontend 'compose.yml'))) {
    throw 'Use a branch fix/geolocation-sprint-validation do frontend, que contém compose.yml.'
}
$docker = 'docker'
if (-not (Get-Command $docker -ErrorAction SilentlyContinue)) {
    $docker = 'C:\Program Files\Docker\Docker\resources\bin\docker.exe'
}
$composeArgs = @('compose', '--env-file', (Join-Path $backend '.env'))
if ($EnvironmentOverride) {
    $composeArgs += @('--env-file', (Resolve-Path -LiteralPath $EnvironmentOverride).Path)
}
Push-Location $backend
try {
    & $docker @composeArgs up -d --build --wait --wait-timeout 180
    if ($LASTEXITCODE -ne 0) { throw 'Backend não ficou saudável; verifique docker compose logs app.' }
} finally { Pop-Location }
Push-Location $frontend
try {
    & $docker compose up -d --build --wait --wait-timeout 120
    if ($LASTEXITCODE -ne 0) { throw 'Frontend não iniciou corretamente.' }
} finally { Pop-Location }
$response = Invoke-WebRequest 'http://localhost:8081/api/v1/service-categories' -UseBasicParsing
if ($response.StatusCode -ne 200) { throw 'Proxy frontend/backend não respondeu.' }
Write-Host 'AllSet disponível em http://localhost:8081; API saudável em http://localhost:8080/actuator/health'
