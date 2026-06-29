param(
    [string]$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path,
    [string]$AdbPath = "",
    [string]$ServiceUrl = "http://10.0.2.2:8081/retailtest/hs/BarcodeTSD",
    [string]$FoundBarcode = "2000000000035",
    [string]$FoundText = "Тестовый товар MVP Found",
    [string]$NotFoundBarcode = "0000000000000",
    [string]$NotFoundText = "Не найдено",
    [string]$AmbiguousBarcode = "2000000000042",
    [string]$AmbiguousText = "Тестовый товар MVP Ambiguous 1",
    [int]$TimeoutSeconds = 30,
    [switch]$SkipBuild,
    [switch]$SkipInstall,
    [switch]$ClearAppData
)

$ErrorActionPreference = "Stop"

function Resolve-AdbPath {
    param([string]$ExplicitPath)

    if (-not [string]::IsNullOrWhiteSpace($ExplicitPath)) {
        return $ExplicitPath
    }

    $candidates = @()
    if (-not [string]::IsNullOrWhiteSpace($env:ANDROID_HOME)) {
        $candidates += (Join-Path $env:ANDROID_HOME "platform-tools\adb.exe")
    }
    if (-not [string]::IsNullOrWhiteSpace($env:ANDROID_SDK_ROOT)) {
        $candidates += (Join-Path $env:ANDROID_SDK_ROOT "platform-tools\adb.exe")
    }
    if (-not [string]::IsNullOrWhiteSpace($env:LOCALAPPDATA)) {
        $candidates += (Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe")
    }
    $candidates += "adb.exe"

    foreach ($candidate in $candidates) {
        $command = Get-Command $candidate -ErrorAction SilentlyContinue
        if ($null -ne $command) {
            return $command.Source
        }
        if (Test-Path -LiteralPath $candidate) {
            return $candidate
        }
    }

    throw "adb.exe was not found. Set -AdbPath or ANDROID_HOME."
}

function Invoke-Native {
    param(
        [Parameter(Mandatory = $true)]
        [string]$FilePath,

        [Parameter(Mandatory = $true)]
        [string[]]$Arguments,

        [string]$Step = $FilePath
    )

    & $FilePath @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "$Step failed with exit code $LASTEXITCODE"
    }
}

function Invoke-Adb {
    param(
        [Parameter(Mandatory = $true)]
        [string[]]$Arguments,

        [string]$Step = "adb"
    )

    $output = & $script:Adb @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "$Step failed with exit code $LASTEXITCODE. Output: $($output -join "`n")"
    }

    return ($output -join "`n")
}

function Get-UiDump {
    Invoke-Adb -Arguments @("shell", "uiautomator", "dump", "/sdcard/window_dump.xml") -Step "Dump Android UI" | Out-Null
    return Invoke-Adb -Arguments @("exec-out", "cat", "/sdcard/window_dump.xml") -Step "Read Android UI dump"
}

function Invoke-AndroidLookup {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Barcode,

        [Parameter(Mandatory = $true)]
        [string]$ExpectedText
    )

    Invoke-Adb -Arguments @("shell", "am", "force-stop", "ru.local.barcodetsd") -Step "Stop Android app" | Out-Null
    Invoke-Adb -Arguments @(
        "shell",
        "am",
        "start",
        "-n",
        "ru.local.barcodetsd/.MainActivity",
        "--es",
        "ru.local.barcodetsd.extra.SERVICE_URL",
        $ServiceUrl,
        "--es",
        "ru.local.barcodetsd.extra.BARCODE",
        $Barcode,
        "--ez",
        "ru.local.barcodetsd.extra.AUTO_LOOKUP",
        "true"
    ) -Step "Start Android smoke lookup" | Out-Null

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    $lastDump = ""
    while ((Get-Date) -lt $deadline) {
        Start-Sleep -Seconds 1
        $lastDump = Get-UiDump
        if ($lastDump -like "*$ExpectedText*") {
            Write-Host "Android smoke scenario passed: $Barcode -> $ExpectedText" -ForegroundColor Green
            return
        }

        if ($lastDump -like "*Ошибка подключения*" -or
            $lastDump -like "*Ошибка сервера*" -or
            $lastDump -like "*Ошибка авторизации*") {
            break
        }
    }

    $dumpPath = Join-Path $env:TEMP "BarcodeTSD_android_ui_dump_$Barcode.xml"
    $lastDump | Set-Content -LiteralPath $dumpPath -Encoding UTF8
    throw "Android smoke did not find expected text '$ExpectedText' for barcode '$Barcode'. UI dump: $dumpPath"
}

$script:Adb = Resolve-AdbPath -ExplicitPath $AdbPath
$androidDir = Join-Path $RepoRoot "android"
$gradle = Join-Path $RepoRoot "android\gradlew.bat"

if (-not $SkipBuild) {
    Push-Location $androidDir
    try {
        Invoke-Native -FilePath $gradle -Arguments @(":app:assembleDebug", "--no-daemon", "--no-configuration-cache") -Step "Build Android APK"
    } finally {
        Pop-Location
    }
}

Invoke-Adb -Arguments @("wait-for-device") -Step "Wait for Android device" | Out-Null
$state = Invoke-Adb -Arguments @("get-state") -Step "Get Android device state"
if ($state.Trim() -ne "device") {
    throw "Android device is not ready. adb state: $state"
}

if (-not $SkipInstall) {
    Push-Location $androidDir
    try {
        Invoke-Native -FilePath $gradle -Arguments @(":app:installDebug", "--no-daemon", "--no-configuration-cache") -Step "Install Android APK"
    } finally {
        Pop-Location
    }
}

if ($ClearAppData) {
    Invoke-Adb -Arguments @("shell", "pm", "clear", "ru.local.barcodetsd") -Step "Clear app data" | Out-Null
}

Invoke-AndroidLookup -Barcode $FoundBarcode -ExpectedText $FoundText
Invoke-AndroidLookup -Barcode $NotFoundBarcode -ExpectedText $NotFoundText
Invoke-AndroidLookup -Barcode $AmbiguousBarcode -ExpectedText $AmbiguousText

Write-Host "Android smoke passed: found, not_found, ambiguous" -ForegroundColor Green
