param(
    [string]$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
)

$ErrorActionPreference = "Stop"

function Read-ProjectFile {
    param(
        [Parameter(Mandatory = $true)]
        [string]$RelativePath
    )

    $path = Join-Path $RepoRoot $RelativePath
    if (-not (Test-Path -LiteralPath $path)) {
        throw "Required file was not found: $RelativePath"
    }

    return [System.IO.File]::ReadAllText($path, [System.Text.Encoding]::UTF8)
}

function Assert-Contains {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Text,

        [Parameter(Mandatory = $true)]
        [string]$Expected,

        [Parameter(Mandatory = $true)]
        [string]$Description
    )

    if (-not $Text.Contains($Expected)) {
        throw "Contract consistency failed: $Description"
    }
}

$openApi = Read-ProjectFile -RelativePath "docs\api\tsd-api.yaml"
$spec = Read-ProjectFile -RelativePath "openspec\specs\tsd-product-lookup\spec.md"
$backend = Read-ProjectFile -RelativePath "extension\src\HTTPServices\BarcodeTSD\Ext\Module.bsl"
$backendSmoke = Read-ProjectFile -RelativePath "docs\testing\run-mvp-smoke.ps1"
$androidSmoke = Read-ProjectFile -RelativePath "docs\testing\run-android-smoke.ps1"

Assert-Contains `
    -Text $openApi `
    -Expected "additionalProperties: false" `
    -Description "OpenAPI request schema must stay strict."
Assert-Contains `
    -Text $spec `
    -Expected "Request contains extra fields" `
    -Description "OpenSpec must describe extra-field rejection."
Assert-Contains `
    -Text $backend `
    -Expected "ДанныеЗапроса.Количество() <> 1" `
    -Description "1C backend must reject request objects with extra fields."
Assert-Contains `
    -Text $backendSmoke `
    -Expected "`"extra`":1" `
    -Description "Backend smoke must cover extra-field rejection."

foreach ($status in @("found", "not_found", "ambiguous")) {
    Assert-Contains `
        -Text $openApi `
        -Expected $status `
        -Description "OpenAPI must document status $status."
}

Assert-Contains `
    -Text $backendSmoke `
    -Expected "2000000000035" `
    -Description "Backend smoke must cover found scenario."
Assert-Contains `
    -Text $backendSmoke `
    -Expected "0000000000000" `
    -Description "Backend smoke must cover not_found scenario."
Assert-Contains `
    -Text $backendSmoke `
    -Expected "2000000000042" `
    -Description "Backend smoke must cover ambiguous scenario."

Assert-Contains `
    -Text $androidSmoke `
    -Expected "FoundBarcode" `
    -Description "Android smoke must cover found scenario."
Assert-Contains `
    -Text $androidSmoke `
    -Expected "NotFoundBarcode" `
    -Description "Android smoke must cover not_found scenario."
Assert-Contains `
    -Text $androidSmoke `
    -Expected "AmbiguousBarcode" `
    -Description "Android smoke must cover ambiguous scenario."

Write-Host "Contract consistency OK"
