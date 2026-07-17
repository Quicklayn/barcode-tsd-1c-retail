[CmdletBinding()]
param(
    [ValidateSet("Fast", "Full", "Mvp")]
    [string]$Mode = "Fast",

    [ValidateSet("Staged", "Range", "Working")]
    [string]$DiffMode = "Working",

    [string]$BaseRef,

    [string]$HeadRef = "HEAD",

    [switch]$RequireCompleteTasks
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$isWindowsPlatform = $env:OS -eq "Windows_NT"

function Invoke-CheckedCommand {
    param(
        [string]$Name,
        [scriptblock]$Command
    )

    Write-Host "`n== $Name =="
    & $Command
    if ($LASTEXITCODE -ne 0) {
        throw "$Name failed with exit code $LASTEXITCODE."
    }
}

function Test-PowerShellSyntax {
    $roots = @(
        (Join-Path $repoRoot "docs\testing"),
        (Join-Path $repoRoot "scripts")
    )

    $files = @($roots | Where-Object { Test-Path -LiteralPath $_ } | ForEach-Object {
        Get-ChildItem -LiteralPath $_ -Filter *.ps1 -File -Recurse
    })

    foreach ($file in $files) {
        $errors = $null
        $tokens = $null
        [System.Management.Automation.Language.Parser]::ParseFile(
            $file.FullName,
            [ref]$tokens,
            [ref]$errors
        ) > $null

        if ($errors.Count -gt 0) {
            throw "PowerShell syntax errors in $($file.FullName): $($errors | Out-String)"
        }
    }

    Write-Host "PowerShell syntax: OK ($($files.Count) scripts)."
}

$localOpenSpec = if ($isWindowsPlatform) {
    Join-Path $repoRoot "node_modules\.bin\openspec.cmd"
} else {
    Join-Path $repoRoot "node_modules/.bin/openspec"
}

if (-not (Test-Path -LiteralPath $localOpenSpec -PathType Leaf)) {
    throw "Project OpenSpec dependency is missing. Run scripts/setup/Initialize-Development.ps1."
}

Push-Location $repoRoot
try {
    Invoke-CheckedCommand -Name "OpenSpec strict validation" -Command {
        & npm run openspec:validate
    }

    Write-Host "`n== PowerShell syntax =="
    Test-PowerShellSyntax

    Invoke-CheckedCommand -Name "1C extension structure" -Command {
        & ".\.codex\skills\1c-metadata-manage\tools\1c-cfe-manage\scripts\cfe-validate.ps1" `
            -ExtensionPath ".\extension\src"
    }

    Invoke-CheckedCommand -Name "1C HTTP service metadata" -Command {
        & ".\.codex\skills\1c-metadata-manage\tools\1c-meta-validate\scripts\meta-validate.ps1" `
            -ObjectPath ".\extension\src\HTTPServices\BarcodeTSD.xml"
    }

    Invoke-CheckedCommand -Name "1C warehouse constant metadata" -Command {
        & ".\.codex\skills\1c-metadata-manage\tools\1c-meta-validate\scripts\meta-validate.ps1" `
            -ObjectPath ".\extension\src\Constants\BarcodeTSD_Склад.xml"
    }

    Invoke-CheckedCommand -Name "1C collection document metadata" -Command {
        & ".\.codex\skills\1c-metadata-manage\tools\1c-meta-validate\scripts\meta-validate.ps1" `
            -ObjectPath ".\extension\src\Documents\BarcodeTSD_СборШтрихкодов.xml"
    }

    Invoke-CheckedCommand -Name "1C accepted sessions register metadata" -Command {
        & ".\.codex\skills\1c-metadata-manage\tools\1c-meta-validate\scripts\meta-validate.ps1" `
            -ObjectPath ".\extension\src\InformationRegisters\BarcodeTSD_ПринятыеСессии.xml"
    }

    Invoke-CheckedCommand -Name "1C TSD role" -Command {
        & ".\.codex\skills\1c-metadata-manage\tools\1c-role-validate\scripts\role-validate.ps1" `
            -RightsPath ".\extension\src\Roles\BarcodeTSD_Use\Ext\Rights.xml"
    }

    Invoke-CheckedCommand -Name "Cross-stack contract consistency" -Command {
        & ".\docs\testing\test-contract-consistency.ps1"
    }

    Write-Host "`n== OpenSpec diff coverage =="
    $coverageArguments = @{
        DiffMode = $DiffMode
        HeadRef = $HeadRef
        RequireCompleteTasks = $RequireCompleteTasks
    }
    if ($BaseRef) {
        $coverageArguments.BaseRef = $BaseRef
    }
    & ".\scripts\quality\Test-OpenSpecCoverage.ps1" @coverageArguments

    if ($Mode -in @("Full", "Mvp")) {
        Invoke-CheckedCommand -Name "Android build, unit tests, and lint" -Command {
            Push-Location ".\android"
            try {
                if ($isWindowsPlatform) {
                    & ".\gradlew.bat" :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug --no-daemon --no-configuration-cache
                } else {
                    & "./gradlew" :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug --no-daemon --no-configuration-cache
                }
            } finally {
                Pop-Location
            }
        }

        Invoke-CheckedCommand -Name "Android APK minSdk" -Command {
            $apkPath = Join-Path $repoRoot "android\app\build\outputs\apk\debug\app-debug.apk"
            $apkAnalyzerCandidates = @(
                (Get-Command apkanalyzer -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Source -ErrorAction SilentlyContinue),
                (Get-Command apkanalyzer.bat -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Source -ErrorAction SilentlyContinue)
            ) | Where-Object { $_ }

            if ($isWindowsPlatform -and $env:LOCALAPPDATA) {
                $apkAnalyzerCandidates += Join-Path $env:LOCALAPPDATA "Android\Sdk\cmdline-tools\latest\bin\apkanalyzer.bat"
            }

            $apkAnalyzer = @($apkAnalyzerCandidates | Where-Object {
                Test-Path -LiteralPath $_ -PathType Leaf
            } | Select-Object -First 1)

            if ($apkAnalyzer.Count -eq 0) {
                throw "Android SDK apkanalyzer is required for the Full quality gate."
            }

            $actualMinSdk = (& $apkAnalyzer[0] manifest min-sdk $apkPath | Out-String).Trim()
            if ($LASTEXITCODE -ne 0 -or $actualMinSdk -ne "26") {
                throw "Expected APK minSdk 26, got '$actualMinSdk'."
            }

            Write-Host "Android APK minSdk: $actualMinSdk"
        }
    }

    if ($Mode -eq "Mvp") {
        Invoke-CheckedCommand -Name "Full MVP smoke" -Command {
            & ".\docs\testing\run-full-mvp-smoke.ps1" -SkipBuild -RecreateInfobase -ClearAppData
        }
    }
} finally {
    Pop-Location
}

Write-Host "`nQuality gate '$Mode': PASSED."
