#requires -Version 7
param(
    [string]$BaseUrl = 'http://localhost:8081',
    [string]$Email = 'cliente@email.com',
    [string]$Password = 'Password123!'
)
$ErrorActionPreference = 'Stop'
if (([uri]$BaseUrl).Host -notin @('localhost', '127.0.0.1')) {
    throw 'Este smoke test cria dados descartáveis e só pode rodar em localhost.'
}
$headers = @{}
function Call-Api($method, $path, $body, $expected = 200) {
    $args = @{ Method=$method; Uri="$BaseUrl$path"; Headers=$headers; SkipHttpErrorCheck=$true; TimeoutSec=45 }
    if ($null -ne $body) { $args.Body = $body | ConvertTo-Json -Depth 10 -Compress; $args.ContentType='application/json; charset=utf-8' }
    $response = Invoke-WebRequest @args
    if ([int]$response.StatusCode -ne $expected) { throw "$method $path retornou $($response.StatusCode); esperado $expected. $($response.Content)" }
    if ($response.Content) { return $response.Content | ConvertFrom-Json }
}
function Assert-Check($condition, $name) {
    if (-not $condition) { throw "FAIL: $name" }
    Write-Host "PASS: $name"
}
$login = Call-Api POST '/api/auth/login' @{email=$Email; password=$Password}
$headers.Authorization = "Bearer $($login.accessToken)"
$payload = $login.accessToken.Split('.')[1].Replace('-', '+').Replace('_', '/')
$payload = $payload.PadRight($payload.Length + (4 - $payload.Length % 4) % 4, '=')
$userId = ([Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($payload)) | ConvertFrom-Json).sub
$path = "/api/users/$userId/addresses"
$category = (Call-Api GET '/api/v1/service-categories' $null).content | Select-Object -First 1
$created = [Collections.Generic.List[string]]::new()
try {
    $base = @{label='Smoke geolocation'; street='Avenida Dom Luís'; number='1233'; district='Aldeota'; city='Fortaleza'; state='CE'; zipCode='60160-230'; isDefault=$false}
    $invalid = $base.Clone(); $invalid.lat=-3.73408; $invalid.lng=-38.49421
    $null = Call-Api POST $path $invalid 400
    Assert-Check $true 'API rejeita coordenadas sem procedência'

    $address = Call-Api POST $path $base 201; $created.Add($address.id)
    Assert-Check (-not $address.expressReady) 'Endereço sem ponto não é elegível para Express'
    $order = @{areaId=$category.areaId; categoryId=$category.id; description='Smoke test'; addressId=$address.id; urgencyFee=0}
    $null = Call-Api POST '/api/v1/orders/express' $order 422
    Assert-Check $true 'Express bloqueia coordenada ausente'

    $approx = $base.Clone(); $approx.lat=-3.71722; $approx.lng=-38.54306; $approx.coordinateSource='geocoded'; $approx.coordinateConfidence='CITY'
    $address = Call-Api POST $path $approx 201; $created.Add($address.id)
    Assert-Check (-not $address.expressReady) 'Centroide municipal permanece aproximado'
    $order.addressId=$address.id
    $null = Call-Api POST '/api/v1/orders/express' $order 422
    Assert-Check $true 'Express rejeita centroide municipal'

    $confirmed = Call-Api PUT "$path/$($address.id)" @{lat=-3.73408; lng=-38.49421; coordinateSource='user_pin'}
    Assert-Check ($confirmed.expressReady -and $confirmed.lat -eq -3.73408) 'Pin confirmado persiste e habilita Express'
    $renamed = Call-Api PUT "$path/$($address.id)" @{label='Smoke renomeado'; street=$base.street}
    Assert-Check $renamed.expressReady 'Reenviar endereço igual preserva confirmação'
    $changed = Call-Api PUT "$path/$($address.id)" @{district='Outro bairro'}
    Assert-Check (-not $changed.expressReady -and $changed.coordinateSource -eq 'legacy' -and $null -eq $changed.coordinateConfirmedAt) 'Mudar bairro invalida pin e confirmação'

    $queries = @(
        @{zipCode='60811-905'; street='Avenida Washington Soares'; number='1321'; district='Edson Queiroz'; city='Fortaleza'; state='CE'},
        @{zipCode='60160-230'; street='Avenida Dom Luís'; number='1233'; district='Aldeota'; city='Fortaleza'; state='CE'},
        @{street='Avenida Beira Mar'; number='2500'; district='Meireles'; city='Fortaleza'; state='CE'}
    )
    foreach ($query in $queries) {
        $result = Call-Api POST '/api/v1/geocoding/lookup' $query
        Assert-Check ($result.lat -gt -7.9 -and $result.lat -lt -2.7 -and $result.lng -gt -41.5 -and $result.lng -lt -37.2) "Lookup público: $($query.street) $($query.number) [$($result.confidence), $($result.provider)]"
        $municipal = [math]::Abs($result.lat + 3.71722) -lt 0.00002 -and [math]::Abs($result.lng + 38.54306) -lt 0.00002
        Assert-Check (-not $municipal -or $result.confidence -ne 'ROOFTOP') 'Centroide nunca se apresenta como imóvel confirmado'
        Write-Host "  Resultado: $($result.lat), $($result.lng) - $($result.displayName)"
    }
    $a = Call-Api POST '/api/v1/geocoding/reverse' @{lat=-3.7687251; lng=-38.4776941}
    $b = Call-Api POST '/api/v1/geocoding/reverse' @{lat=-3.7687249; lng=-38.4776939}
    Assert-Check ($a.lat -eq -3.7687251 -and $a.lng -eq -38.4776941 -and $b.lat -eq -3.7687249 -and $b.lng -eq -38.4776939) 'Reverse com cache preserva cada pin exato'
    Write-Host 'SMOKE GEOLOCATION PASS'
} finally {
    foreach ($id in $created) { $null = Call-Api DELETE "$path/$id" $null 204 }
}
