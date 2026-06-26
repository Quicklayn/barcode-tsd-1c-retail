param(
    [string]$InfoBasePath = (Join-Path $env:TEMP "BarcodeTSD_MVP\ib"),
    [string]$ConnectionString = "",
    [string]$Barcode = "2000000000011",
    [string]$Name = "Тестовый товар MVP"
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

if ([string]::IsNullOrWhiteSpace($ConnectionString)) {
    $databaseFile = Join-Path $InfoBasePath "1Cv8.1CD"
    if (-not (Test-Path -LiteralPath $databaseFile)) {
        throw "Infobase file was not found: $databaseFile"
    }

    $ConnectionString = "File='$InfoBasePath';"
}

$connector = New-Object -ComObject V83.COMConnector
$connection = $connector.Connect($ConnectionString)

$query = Invoke-OneCMethod -Object $connection -Name "NewObject" -Arguments @("Запрос")
Set-OneCProperty -Object $query -Name "Текст" -Value @"
ВЫБРАТЬ ПЕРВЫЕ 1
	ШтрихкодыНоменклатуры.Номенклатура.Наименование КАК Наименование
ИЗ
	РегистрСведений.ШтрихкодыНоменклатуры КАК ШтрихкодыНоменклатуры
ГДЕ
	ШтрихкодыНоменклатуры.Штрихкод = &Штрихкод
"@
Invoke-OneCMethod -Object $query -Name "УстановитьПараметр" -Arguments @("Штрихкод", $Barcode) | Out-Null
$queryResult = Invoke-OneCMethod -Object $query -Name "Выполнить"
$selection = Invoke-OneCMethod -Object $queryResult -Name "Выбрать"

if (Invoke-OneCMethod -Object $selection -Name "Следующий") {
    $existingName = Get-OneCProperty -Object $selection -Name "Наименование"
    [pscustomobject]@{
        Status = "exists"
        Barcode = $Barcode
        Name = $existingName
    }
    return
}

$catalogs = Get-OneCProperty -Object $connection -Name "Справочники"
$items = Get-OneCProperty -Object $catalogs -Name "Номенклатура"
$item = Invoke-OneCMethod -Object $items -Name "СоздатьЭлемент"
$exchangeData = Get-OneCProperty -Object $item -Name "ОбменДанными"

Set-OneCProperty -Object $exchangeData -Name "Загрузка" -Value $true
Set-OneCProperty -Object $item -Name "Наименование" -Value $Name
Set-OneCProperty -Object $item -Name "НаименованиеПолное" -Value $Name
Invoke-OneCMethod -Object $item -Name "Записать" | Out-Null

$itemRef = Get-OneCProperty -Object $item -Name "Ссылка"
$registers = Get-OneCProperty -Object $connection -Name "РегистрыСведений"
$barcodes = Get-OneCProperty -Object $registers -Name "ШтрихкодыНоменклатуры"
$record = Invoke-OneCMethod -Object $barcodes -Name "СоздатьМенеджерЗаписи"

Set-OneCProperty -Object $record -Name "Штрихкод" -Value $Barcode
Set-OneCProperty -Object $record -Name "Номенклатура" -Value $itemRef
Invoke-OneCMethod -Object $record -Name "Записать" | Out-Null

[pscustomobject]@{
    Status = "created"
    Barcode = $Barcode
    Name = $Name
}
