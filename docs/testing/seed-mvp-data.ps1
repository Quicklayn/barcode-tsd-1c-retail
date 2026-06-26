param(
    [string]$InfoBasePath = (Join-Path $env:TEMP "BarcodeTSD_MVP\ib"),
    [string]$ConnectionString = "",
    [string]$Barcode = "2000000000011",
    [string]$Name = "Тестовый товар MVP",
    [switch]$Ambiguous
)

$ErrorActionPreference = "Stop"

$script:InvokeMethod = [System.Reflection.BindingFlags]"InvokeMethod"
$script:GetProperty = [System.Reflection.BindingFlags]"GetProperty"
$script:SetProperty = [System.Reflection.BindingFlags]"SetProperty"

function Invoke-OneCMethod {
    param(
        [Parameter(Mandatory = $true)]
        [object]$Object,

        [Parameter(Mandatory = $true)]
        [string]$Name,

        [object[]]$Arguments = @()
    )

    $result = $Object.GetType().InvokeMember($Name, $script:InvokeMethod, $null, $Object, $Arguments)
    return ,$result
}

function Get-OneCProperty {
    param(
        [Parameter(Mandatory = $true)]
        [object]$Object,

        [Parameter(Mandatory = $true)]
        [string]$Name
    )

    $result = $Object.GetType().InvokeMember($Name, $script:GetProperty, $null, $Object, @())
    return ,$result
}

function Set-OneCProperty {
    param(
        [Parameter(Mandatory = $true)]
        [object]$Object,

        [Parameter(Mandatory = $true)]
        [string]$Name,

        [object]$Value
    )

    [void]$Object.GetType().InvokeMember($Name, $script:SetProperty, $null, $Object, @($Value))
}

function Find-BarcodeMatches {
    param(
        [Parameter(Mandatory = $true)]
        [object]$Connection,

        [Parameter(Mandatory = $true)]
        [string]$Barcode
    )

    $query = Invoke-OneCMethod -Object $Connection -Name "NewObject" -Arguments @("Запрос")
    $queryText = @(
        "ВЫБРАТЬ РАЗЛИЧНЫЕ",
        "	ШтрихкодыНоменклатуры.Номенклатура.Наименование КАК Наименование",
        "ИЗ",
        "	РегистрСведений.ШтрихкодыНоменклатуры КАК ШтрихкодыНоменклатуры",
        "ГДЕ",
        "	ШтрихкодыНоменклатуры.Штрихкод = &Штрихкод",
        "",
        "УПОРЯДОЧИТЬ ПО",
        "	Наименование"
    ) -join [Environment]::NewLine
    Set-OneCProperty -Object $query -Name "Текст" -Value $queryText
    Invoke-OneCMethod -Object $query -Name "УстановитьПараметр" -Arguments @("Штрихкод", $Barcode) | Out-Null
    $queryResult = Invoke-OneCMethod -Object $query -Name "Выполнить"
    $selection = Invoke-OneCMethod -Object $queryResult -Name "Выбрать"
    $matches = @()

    while (Invoke-OneCMethod -Object $selection -Name "Следующий") {
        $matches += [string](Get-OneCProperty -Object $selection -Name "Наименование")
    }

    return $matches
}

function New-TestBarcodeItem {
    param(
        [Parameter(Mandatory = $true)]
        [object]$Connection,

        [Parameter(Mandatory = $true)]
        [string]$Barcode,

        [Parameter(Mandatory = $true)]
        [string]$Name
    )

    $catalogs = Get-OneCProperty -Object $Connection -Name "Справочники"
    $items = Get-OneCProperty -Object $catalogs -Name "Номенклатура"
    $item = Invoke-OneCMethod -Object $items -Name "СоздатьЭлемент"
    $exchangeData = Get-OneCProperty -Object $item -Name "ОбменДанными"

    Set-OneCProperty -Object $exchangeData -Name "Загрузка" -Value $true
    Set-OneCProperty -Object $item -Name "Наименование" -Value $Name
    Set-OneCProperty -Object $item -Name "НаименованиеПолное" -Value $Name
    Invoke-OneCMethod -Object $item -Name "Записать" | Out-Null

    $itemRef = Get-OneCProperty -Object $item -Name "Ссылка"
    $registers = Get-OneCProperty -Object $Connection -Name "РегистрыСведений"
    $barcodes = Get-OneCProperty -Object $registers -Name "ШтрихкодыНоменклатуры"
    $record = Invoke-OneCMethod -Object $barcodes -Name "СоздатьМенеджерЗаписи"

    Set-OneCProperty -Object $record -Name "Штрихкод" -Value $Barcode
    Set-OneCProperty -Object $record -Name "Номенклатура" -Value $itemRef
    Invoke-OneCMethod -Object $record -Name "Записать" | Out-Null
}

if ([string]::IsNullOrWhiteSpace($ConnectionString)) {
    $databaseFile = Join-Path $InfoBasePath "1Cv8.1CD"
    if (-not (Test-Path -LiteralPath $databaseFile)) {
        throw "Infobase file was not found: $databaseFile"
    }

    $ConnectionString = "File='$InfoBasePath';"
}

$connector = New-Object -ComObject V83.COMConnector
$connection = $connector.Connect($ConnectionString)

$requiredMatches = 1
if ($Ambiguous) {
    $requiredMatches = 2
}

$matches = @(Find-BarcodeMatches -Connection $connection -Barcode $Barcode)
$createdCount = 0

while ($matches.Count -lt $requiredMatches) {
    if ($Ambiguous) {
        $itemName = "$Name $($matches.Count + 1)"
    } else {
        $itemName = $Name
    }

    New-TestBarcodeItem -Connection $connection -Barcode $Barcode -Name $itemName
    $createdCount++
    $matches = @(Find-BarcodeMatches -Connection $connection -Barcode $Barcode)
}

$status = "exists"
if ($createdCount -gt 0) {
    $status = "created"
}

if ($Ambiguous -and $createdCount -eq 0) {
    $status = "ambiguous_exists"
} elseif ($Ambiguous) {
    $status = "ambiguous_created"
}

[pscustomobject]@{
    Status = $status
    Barcode = $Barcode
    MatchCount = $matches.Count
    Names = ($matches -join "; ")
}
