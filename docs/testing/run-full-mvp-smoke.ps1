param(
    [string]$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path,
    [string]$V8Path = "C:\Program Files\1cv8\8.3.27.2130\bin",
    [string]$WorkPath = (Join-Path $env:TEMP "BarcodeTSD_MVP"),
    [string]$AppName = "RetailTest",
    [int]$Port = 8081,
    [int[]]$FallbackPorts = @(18081, 18082, 18083),
    [string]$AdbPath = "",
    [switch]$RecreateInfobase,
    [switch]$SkipBuild,
    [switch]$SkipInstall,
    [switch]$ClearAppData,
    [switch]$KeepWebServer
)

$ErrorActionPreference = "Stop"

function Stop-SmokeApache {
    param(
        [Parameter(Mandatory = $true)]
        [string]$RepoRoot,

        [Parameter(Mandatory = $true)]
        [string]$WorkPath
    )

    $apachePath = Join-Path $WorkPath "apache24"
    $stopScript = Join-Path $RepoRoot ".codex\skills\1c-metadata-manage\tools\1c-web-ops\scripts\web-stop.ps1"
    if (Test-Path -LiteralPath $stopScript) {
        & $stopScript -ApachePath $apachePath | Out-Host
    }
}

function Find-PublishedPort {
    param(
        [Parameter(Mandatory = $true)]
        [string]$AppName,

        [Parameter(Mandatory = $true)]
        [int[]]$Ports
    )

    $appPath = $AppName.ToLowerInvariant()
    $body = @{ barcode = "0000000000000" } | ConvertTo-Json -Compress
    foreach ($candidatePort in ($Ports | Select-Object -Unique)) {
        $url = "http://localhost:$candidatePort/$appPath/hs/BarcodeTSD/v1/barcode/resolve"
        try {
            $response = Invoke-WebRequest `
                -Uri $url `
                -Method Post `
                -Body $body `
                -ContentType "application/json; charset=utf-8" `
                -UseBasicParsing `
                -TimeoutSec 5
            if ($response.StatusCode -eq 200) {
                return $candidatePort
            }
        } catch {
            continue
        }
    }

    throw "Cannot detect published backend port."
}

$backendScript = Join-Path $RepoRoot "docs\testing\run-mvp-smoke.ps1"
$androidScript = Join-Path $RepoRoot "docs\testing\run-android-smoke.ps1"

if (-not (Test-Path -LiteralPath $backendScript)) {
    throw "Backend smoke script was not found: $backendScript"
}

if (-not (Test-Path -LiteralPath $androidScript)) {
    throw "Android smoke script was not found: $androidScript"
}

try {
    $backendArgs = @{
        RepoRoot = $RepoRoot
        V8Path = $V8Path
        WorkPath = $WorkPath
        AppName = $AppName
        Port = $Port
        FallbackPorts = $FallbackPorts
        KeepWebServer = $true
    }
    if ($RecreateInfobase) {
        $backendArgs.RecreateInfobase = $true
    }

    $backendOutput = & $backendScript @backendArgs 2>&1
    $backendOutput | Out-Host

    $portsToProbe = @($Port) + $FallbackPorts
    $publishedPort = Find-PublishedPort -AppName $AppName -Ports $portsToProbe
    $publishedApp = $AppName.ToLowerInvariant()
    $serviceUrl = "http://10.0.2.2:$publishedPort/$publishedApp/hs/BarcodeTSD"

    $androidArgs = @{
        RepoRoot = $RepoRoot
        ServiceUrl = $serviceUrl
    }
    if (-not [string]::IsNullOrWhiteSpace($AdbPath)) {
        $androidArgs.AdbPath = $AdbPath
    }
    if ($SkipBuild) {
        $androidArgs.SkipBuild = $true
    }
    if ($SkipInstall) {
        $androidArgs.SkipInstall = $true
    }
    if ($ClearAppData) {
        $androidArgs.ClearAppData = $true
    }

    & $androidScript @androidArgs
    Write-Host "Full MVP smoke passed." -ForegroundColor Green
} finally {
    if (-not $KeepWebServer) {
        Stop-SmokeApache -RepoRoot $RepoRoot -WorkPath $WorkPath
    }
}
