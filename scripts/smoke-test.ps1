param([string]$Username = 'admin', [string]$Password = 'admin')
$ErrorActionPreference = 'Stop'
$baseUrl = 'http://127.0.0.1:8080'
$results = [Collections.Generic.List[object]]::new()

function Add-Result([string]$Item, [bool]$Passed, [string]$Detail) {
    $results.Add([pscustomobject]@{ Item = $Item; Result = $(if ($Passed) { 'PASS' } else { 'FAIL' }); Detail = $Detail })
    if (-not $Passed) { throw "$Item failed: $Detail" }
}

$health = Invoke-RestMethod "$baseUrl/actuator/health" -TimeoutSec 5
Add-Result 'Backend health' ($health.status -eq 'UP') $health.status

$loginBody = @{ username = $Username; password = $Password } | ConvertTo-Json
$login = Invoke-RestMethod "$baseUrl/api/auth/login" -Method Post -ContentType 'application/json' -Body $loginBody -TimeoutSec 5
Add-Result 'Authentication' ([bool]$login.token) 'token issued'
$headers = @{ Authorization = "Bearer $($login.token)" }

$devices = Invoke-RestMethod "$baseUrl/api/devices" -Headers $headers -TimeoutSec 5
Add-Result 'Devices' ($devices.Count -gt 0) "$($devices.Count) configured"
$points = @()
$values = @()
foreach ($device in $devices) {
    $points += Invoke-RestMethod "$baseUrl/api/points?deviceId=$($device.id)" -Headers $headers -TimeoutSec 5
    $values += Invoke-RestMethod "$baseUrl/api/realtime/values?deviceId=$($device.id)" -Headers $headers -TimeoutSec 5
}
Add-Result 'Collection points' ($points.Count -gt 0) "$($points.Count) configured"
$goodValues = @($values | Where-Object { $_.quality -eq 'GOOD' }).Count
Add-Result 'Realtime values' ($values.Count -gt 0 -and $goodValues -gt 0) "$($values.Count) values, $goodValues GOOD"
$alarms = @(Invoke-RestMethod "$baseUrl/api/alarms/active" -Headers $headers -TimeoutSec 5)
Add-Result 'Active alarms' $true "$($alarms.Count) active"
$screens = Invoke-RestMethod "$baseUrl/api/hmi/screens" -Headers $headers -TimeoutSec 5
Add-Result 'HMI screens' ($screens.Count -gt 0) "$($screens.Count) configured"
$screenId = $screens[0].id
$published = Invoke-RestMethod "$baseUrl/api/hmi/config/published?screenId=$screenId" -Headers $headers -TimeoutSec 5
$items = @($published.document.items)
Add-Result 'Published HMI' ($published.version -and $items.Count -gt 0) "version $($published.version), $($items.Count) items"

$results | Format-Table -AutoSize
Write-Output "MVP smoke test passed $($results.Count)/$($results.Count)."
