[CmdletBinding()]
param(
    [switch]$SkipValidation
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path

Push-Location $repoRoot
try {
    & npm ci --ignore-scripts --no-audit --no-fund
    if ($LASTEXITCODE -ne 0) {
        throw "npm ci failed with exit code $LASTEXITCODE."
    }

    & git config core.hooksPath .githooks
    if ($LASTEXITCODE -ne 0) {
        throw "Unable to configure core.hooksPath."
    }

    $hooksPath = (& git config --get core.hooksPath).Trim()
    if ($hooksPath -ne ".githooks") {
        throw "Unexpected core.hooksPath: $hooksPath"
    }

    Write-Host "Git hooks: $hooksPath"

    if (-not $SkipValidation) {
        & ".\scripts\quality\Invoke-QualityGate.ps1" -Mode Fast -DiffMode Working
        if ($LASTEXITCODE -ne 0) {
            throw "Fast quality gate failed with exit code $LASTEXITCODE."
        }
    }
} finally {
    Pop-Location
}

Write-Host "Development environment initialized."
