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

function Select-RequiredXmlText {
    param(
        [Parameter(Mandatory = $true)]
        [xml]$Xml,

        [Parameter(Mandatory = $true)]
        [System.Xml.XmlNamespaceManager]$NamespaceManager,

        [Parameter(Mandatory = $true)]
        [string]$XPath,

        [Parameter(Mandatory = $true)]
        [string]$Description
    )

    $node = $Xml.SelectSingleNode($XPath, $NamespaceManager)
    if ($null -eq $node) {
        throw "Contract consistency failed: $Description"
    }

    return $node.InnerText
}

function Assert-Equals {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Actual,

        [Parameter(Mandatory = $true)]
        [string]$Expected,

        [Parameter(Mandatory = $true)]
        [string]$Description
    )

    if ($Actual -ne $Expected) {
        throw "Contract consistency failed: $Description Expected '$Expected', got '$Actual'."
    }
}

$openApi = Read-ProjectFile -RelativePath "docs\api\tsd-api.yaml"
$spec = Read-ProjectFile -RelativePath "openspec\specs\tsd-product-lookup\spec.md"
$httpServiceXmlText = Read-ProjectFile -RelativePath "extension\src\HTTPServices\BarcodeTSD.xml"
$backend = Read-ProjectFile -RelativePath "extension\src\HTTPServices\BarcodeTSD\Ext\Module.bsl"
$androidClient = Read-ProjectFile -RelativePath "android\app\src\main\java\ru\local\barcodetsd\BarcodeLookupClient.kt"
$backendSmoke = Read-ProjectFile -RelativePath "docs\testing\run-mvp-smoke.ps1"
$androidSmoke = Read-ProjectFile -RelativePath "docs\testing\run-android-smoke.ps1"

[xml]$httpServiceXml = $httpServiceXmlText
$namespaceManager = New-Object System.Xml.XmlNamespaceManager($httpServiceXml.NameTable)
$namespaceManager.AddNamespace("md", "http://v8.1c.ru/8.3/MDClasses")

Assert-Equals `
    -Actual (Select-RequiredXmlText `
        -Xml $httpServiceXml `
        -NamespaceManager $namespaceManager `
        -XPath "//md:HTTPService/md:Properties/md:RootURL" `
        -Description "HTTP service RootURL must exist.") `
    -Expected "BarcodeTSD" `
    -Description "HTTP service RootURL must stay BarcodeTSD."
Assert-Equals `
    -Actual (Select-RequiredXmlText `
        -Xml $httpServiceXml `
        -NamespaceManager $namespaceManager `
        -XPath "//md:HTTPService/md:ChildObjects/md:URLTemplate/md:Properties/md:Template" `
        -Description "HTTP service URL template must exist.") `
    -Expected "/v1/barcode/resolve" `
    -Description "HTTP service URL template must match OpenAPI path."
Assert-Equals `
    -Actual (Select-RequiredXmlText `
        -Xml $httpServiceXml `
        -NamespaceManager $namespaceManager `
        -XPath "//md:HTTPService/md:ChildObjects/md:URLTemplate/md:ChildObjects/md:Method/md:Properties/md:HTTPMethod" `
        -Description "HTTP service method must exist.") `
    -Expected "POST" `
    -Description "HTTP service method must stay POST."
Assert-Equals `
    -Actual (Select-RequiredXmlText `
        -Xml $httpServiceXml `
        -NamespaceManager $namespaceManager `
        -XPath "//md:HTTPService/md:ChildObjects/md:URLTemplate/md:ChildObjects/md:Method/md:Properties/md:Handler" `
        -Description "HTTP service handler must exist.") `
    -Expected "BarcodeResolve" `
    -Description "HTTP service handler must stay BarcodeResolve."

Assert-Contains `
    -Text $openApi `
    -Expected "default: retailtest" `
    -Description "OpenAPI infobase default must match smoke publication casing."
Assert-Contains `
    -Text $openApi `
    -Expected "/v1/barcode/resolve:" `
    -Description "OpenAPI must document the barcode resolve path."
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
    -Text $androidClient `
    -Expected 'private const val RESOLVE_PATH = "/v1/barcode/resolve"' `
    -Description "Android client endpoint path must match OpenAPI and HTTP service XML."
Assert-Contains `
    -Text $backendSmoke `
    -Expected "`"extra`":1" `
    -Description "Backend smoke must cover extra-field rejection."
Assert-Contains `
    -Text $backendSmoke `
    -Expected "-Body `"{}`"" `
    -Description "Backend smoke must cover empty-object rejection."
Assert-Contains `
    -Text $backendSmoke `
    -Expected "`"code`":`"2000000000035`"" `
    -Description "Backend smoke must cover wrong-field rejection."
Assert-Contains `
    -Text $backendSmoke `
    -Expected "`"barcode`":123" `
    -Description "Backend smoke must cover non-string barcode rejection."
Assert-Contains `
    -Text $backendSmoke `
    -Expected "-Body `"[]`"" `
    -Description "Backend smoke must cover root-array rejection."

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
