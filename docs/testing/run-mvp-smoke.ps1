param(
    [string]$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path,
    [string]$V8Path = "C:\Program Files\1cv8\8.3.27.2130\bin",
    [string]$WorkPath = (Join-Path $env:TEMP "BarcodeTSD_MVP"),
    [string]$AppName = "RetailTest",
    [int]$Port = 8081,
    [int[]]$FallbackPorts = @(18081, 18082, 18083),
    [switch]$RecreateInfobase,
    [switch]$KeepWebServer
)

$ErrorActionPreference = "Stop"

function Invoke-Native {
    param(
        [Parameter(Mandatory = $true)]
        [string]$FilePath,

        [Parameter(Mandatory = $true)]
        [string[]]$Arguments,

        [string]$Step = $FilePath,
        [switch]$AllowFailure
    )

    & $FilePath @Arguments
    $exitCode = $LASTEXITCODE
    if ($exitCode -ne 0 -and -not $AllowFailure) {
        throw "$Step failed with exit code $exitCode"
    }

    if ($AllowFailure) {
        return $exitCode
    }
}

function Stop-SmokeApache {
    param(
        [Parameter(Mandatory = $true)]
        [string]$ApachePath
    )

    $stopScript = Join-Path $RepoRoot ".codex\skills\1c-metadata-manage\tools\1c-web-ops\scripts\web-stop.ps1"
    if (Test-Path -LiteralPath $stopScript) {
        & $stopScript -ApachePath $ApachePath | Out-Host
    }
}

function Test-ExtensionExists {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Ibcmd,

        [Parameter(Mandatory = $true)]
        [string]$InfoBasePath
    )

    & $Ibcmd extension info --database-path=$InfoBasePath --name=BarcodeTSD *> $null
    return $LASTEXITCODE -eq 0
}

function Invoke-SmokeRequest {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Url,

        [Parameter(Mandatory = $true)]
        [string]$Body
    )

    try {
        $response = Invoke-WebRequest `
            -Uri $Url `
            -Method Post `
            -Body $Body `
            -ContentType "application/json; charset=utf-8" `
            -UseBasicParsing

        return [pscustomobject]@{
            StatusCode = [int]$response.StatusCode
            Content = [string]$response.Content
        }
    } catch [System.Net.WebException] {
        $errorResponse = $_.Exception.Response
        if ($null -eq $errorResponse) {
            throw
        }

        $content = [string]$_.ErrorDetails.Message
        if ([string]::IsNullOrWhiteSpace($content)) {
            $stream = $errorResponse.GetResponseStream()
            if ($null -ne $stream) {
                $reader = New-Object System.IO.StreamReader($stream, [System.Text.Encoding]::UTF8)
                try {
                    $content = $reader.ReadToEnd()
                } finally {
                    $reader.Dispose()
                    $stream.Dispose()
                }
            }
        }

        try {
            return [pscustomobject]@{
                StatusCode = [int]$errorResponse.StatusCode
                Content = $content
            }
        } finally {
            $errorResponse.Dispose()
        }
    }
}

function Assert-BarcodeResponse {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Url,

        [Parameter(Mandatory = $true)]
        [AllowEmptyString()]
        [string]$Barcode,

        [Parameter(Mandatory = $true)]
        [int]$ExpectedCode,

        [string]$ExpectedStatus = "",
        [string]$ExpectedError = "",
        [string]$ExpectedName = ""
    )

    $body = @{ barcode = $Barcode } | ConvertTo-Json -Compress
    $response = Invoke-SmokeRequest -Url $Url -Body $body

    if ($response.StatusCode -ne $ExpectedCode) {
        throw "Barcode '$Barcode' returned HTTP $($response.StatusCode), expected $ExpectedCode. Body: $($response.Content)"
    }

    $json = $response.Content | ConvertFrom-Json
    if ($ExpectedStatus -and $json.status -ne $ExpectedStatus) {
        throw "Barcode '$Barcode' returned status '$($json.status)', expected '$ExpectedStatus'."
    }

    if ($ExpectedError -and $json.error -ne $ExpectedError) {
        throw "Barcode '$Barcode' returned error '$($json.error)', expected '$ExpectedError'."
    }

    if ($ExpectedName) {
        $names = @($json.matches | ForEach-Object { $_.name })
        $hasName = $names -contains $ExpectedName
        if (-not $hasName) {
            throw "Barcode '$Barcode' response does not contain expected name '$ExpectedName'."
        }
    }

    $matchCount = 0
    if ($null -ne $json.matches) {
        $matchCount = @($json.matches).Count
    }

    [pscustomobject]@{
        Barcode = $Barcode
        HttpCode = $response.StatusCode
        Status = $json.status
        MatchCount = $matchCount
    }
}

function Assert-RawBarcodeResponse {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Url,

        [Parameter(Mandatory = $true)]
        [string]$Body,

        [Parameter(Mandatory = $true)]
        [int]$ExpectedCode,

        [string]$ExpectedError = ""
    )

    $response = Invoke-SmokeRequest -Url $Url -Body $Body

    if ($response.StatusCode -ne $ExpectedCode) {
        throw "Raw request returned HTTP $($response.StatusCode), expected $ExpectedCode. Body: $($response.Content)"
    }

    $json = $response.Content | ConvertFrom-Json
    if ($ExpectedError -and $json.error -ne $ExpectedError) {
        throw "Raw request returned error '$($json.error)', expected '$ExpectedError'."
    }

    [pscustomobject]@{
        Barcode = "<raw>"
        HttpCode = $response.StatusCode
        Status = $json.status
        MatchCount = 0
    }
}

function Publish-SmokeWeb {
    param(
        [Parameter(Mandatory = $true)]
        [string]$PublishScript,

        [Parameter(Mandatory = $true)]
        [string]$V8Path,

        [Parameter(Mandatory = $true)]
        [string]$InfoBasePath,

        [Parameter(Mandatory = $true)]
        [string]$AppName,

        [Parameter(Mandatory = $true)]
        [string]$ApachePath,

        [Parameter(Mandatory = $true)]
        [int[]]$Ports
    )

    foreach ($candidatePort in $Ports) {
        & $PublishScript `
            -V8Path $V8Path `
            -InfoBasePath $InfoBasePath `
            -AppName $AppName `
            -ApachePath $ApachePath `
            -Port $candidatePort | Out-Host

        if ($LASTEXITCODE -eq 0) {
            return $candidatePort
        }

        Stop-SmokeApache -ApachePath $ApachePath
        Write-Host "Порт $candidatePort недоступен, пробую следующий." -ForegroundColor Yellow
    }

    throw "Cannot publish 1C web service on ports: $($Ports -join ', ')"
}

$ibcmd = Join-Path $V8Path "ibcmd.exe"
$rt3Path = Join-Path $RepoRoot "RT3"
$extensionPath = Join-Path $RepoRoot "extension\src"
$ibPath = Join-Path $WorkPath "ib"
$apachePath = Join-Path $WorkPath "apache24"
$publishScript = Join-Path $RepoRoot ".codex\skills\1c-metadata-manage\tools\1c-web-ops\scripts\web-publish.ps1"
$seedScript = Join-Path $RepoRoot "docs\testing\seed-mvp-data.ps1"
$databaseFile = Join-Path $ibPath "1Cv8.1CD"

if (-not (Test-Path -LiteralPath $ibcmd)) {
    throw "ibcmd.exe was not found: $ibcmd"
}

if (-not (Test-Path -LiteralPath $rt3Path)) {
    throw "RT3 export was not found: $rt3Path"
}

if (-not (Test-Path -LiteralPath $extensionPath)) {
    throw "Extension source was not found: $extensionPath"
}

if (-not (Test-Path -LiteralPath $publishScript)) {
    throw "Web publish script was not found: $publishScript"
}

if (-not (Test-Path -LiteralPath $seedScript)) {
    throw "Seed script was not found: $seedScript"
}

$workRoot = [System.IO.Path]::GetFullPath($WorkPath)
$tempRoot = [System.IO.Path]::GetFullPath((Join-Path $env:TEMP "BarcodeTSD_MVP"))
if (-not $workRoot.StartsWith($tempRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "WorkPath must stay under $tempRoot for this smoke script."
}

try {
    if ($RecreateInfobase -and (Test-Path -LiteralPath $ibPath)) {
        Stop-SmokeApache -ApachePath $apachePath
        Remove-Item -LiteralPath $ibPath -Recurse -Force
    }

    if (-not (Test-Path -LiteralPath $databaseFile)) {
        New-Item -ItemType Directory -Force -Path $ibPath | Out-Null
        Invoke-Native `
            -FilePath $ibcmd `
            -Arguments @("infobase", "create", "--database-path=$ibPath", "--import=$rt3Path", "--apply", "--force") `
            -Step "Create temporary infobase"
    }

    if (-not (Test-ExtensionExists -Ibcmd $ibcmd -InfoBasePath $ibPath)) {
        Invoke-Native `
            -FilePath $ibcmd `
            -Arguments @("config", "extension", "create", "--database-path=$ibPath", "--name=BarcodeTSD", "--name-prefix=BarcodeTSD_", "--purpose=customization") `
            -Step "Create BarcodeTSD extension"
    }

    Invoke-Native `
        -FilePath $ibcmd `
        -Arguments @("config", "import", "--database-path=$ibPath", "--extension=BarcodeTSD", $extensionPath) `
        -Step "Import BarcodeTSD extension"
    Invoke-Native `
        -FilePath $ibcmd `
        -Arguments @("config", "check", "--database-path=$ibPath", "--extension=BarcodeTSD", "--force") `
        -Step "Check BarcodeTSD extension"
    Invoke-Native `
        -FilePath $ibcmd `
        -Arguments @("config", "apply", "--database-path=$ibPath", "--extension=BarcodeTSD", "--dynamic=disable", "--force", "--session-terminate=force") `
        -Step "Apply BarcodeTSD extension"
    Invoke-Native `
        -FilePath $ibcmd `
        -Arguments @("extension", "update", "--database-path=$ibPath", "--name=BarcodeTSD", "--active=yes") `
        -Step "Activate BarcodeTSD extension"

    $portsToTry = @($Port) + $FallbackPorts | Select-Object -Unique
    $publishedPort = Publish-SmokeWeb `
        -PublishScript $publishScript `
        -V8Path $V8Path `
        -InfoBasePath $ibPath `
        -AppName $AppName `
        -ApachePath $apachePath `
        -Ports $portsToTry

    & $seedScript -InfoBasePath $ibPath -Barcode "2000000000035" -Name "Тестовый товар MVP Found" | Out-Host
    & $seedScript -InfoBasePath $ibPath -Barcode "2000000000042" -Name "Тестовый товар MVP Ambiguous" -Ambiguous | Out-Host

    $rootUrl = "http://localhost:$publishedPort/$($AppName.ToLowerInvariant())"
    $url = "$rootUrl/hs/BarcodeTSD/v1/barcode/resolve"
    $results = @(
        Assert-RawBarcodeResponse -Url $url -Body "{" -ExpectedCode 400 -ExpectedError "invalid_request"
        Assert-RawBarcodeResponse -Url $url -Body "{}" -ExpectedCode 400 -ExpectedError "invalid_request"
        Assert-RawBarcodeResponse -Url $url -Body '{"code":"2000000000035"}' -ExpectedCode 400 -ExpectedError "invalid_request"
        Assert-RawBarcodeResponse -Url $url -Body '{"barcode":123}' -ExpectedCode 400 -ExpectedError "invalid_request"
        Assert-RawBarcodeResponse -Url $url -Body "[]" -ExpectedCode 400 -ExpectedError "invalid_request"
        Assert-RawBarcodeResponse -Url $url -Body '"2000000000035"' -ExpectedCode 400 -ExpectedError "invalid_request"
        Assert-RawBarcodeResponse `
            -Url $url `
            -Body '{"barcode":"2000000000035","extra":1}' `
            -ExpectedCode 400 `
            -ExpectedError "invalid_request"
        Assert-BarcodeResponse -Url $url -Barcode "" -ExpectedCode 400 -ExpectedError "invalid_request"
        Assert-BarcodeResponse -Url $url -Barcode ("9" * 201) -ExpectedCode 400 -ExpectedError "invalid_request"
        Assert-BarcodeResponse -Url $url -Barcode "0000000000000" -ExpectedCode 200 -ExpectedStatus "not_found"
        Assert-BarcodeResponse -Url $url -Barcode "2000000000035" -ExpectedCode 200 -ExpectedStatus "found" -ExpectedName "Тестовый товар MVP Found"
        Assert-BarcodeResponse -Url $url -Barcode "2000000000042" -ExpectedCode 200 -ExpectedStatus "ambiguous" -ExpectedName "Тестовый товар MVP Ambiguous 1"
    )

    $results | Format-Table -AutoSize
    Write-Host "MVP smoke passed: $url" -ForegroundColor Green
} finally {
    if (-not $KeepWebServer) {
        Stop-SmokeApache -ApachePath $apachePath
    }
}
