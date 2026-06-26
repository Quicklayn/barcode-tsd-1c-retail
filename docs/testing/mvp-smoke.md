# MVP Smoke Test

Этот сценарий проверяет backend без изменения `RT3/` и без записи временной ИБ
в репозиторий.

## 1. Создать временную ИБ из `RT3`

```powershell
$repo = "E:\Projects\Barcode_app"
$ibcmd = "C:\Program Files\1cv8\8.3.27.2130\bin\ibcmd.exe"
$work = Join-Path $env:TEMP "BarcodeTSD_MVP"
$ib = Join-Path $work "ib"
New-Item -ItemType Directory -Force -Path $ib | Out-Null

& $ibcmd infobase create --database-path=$ib --import="$repo\RT3" --apply --force
```

## 2. Загрузить расширение

```powershell
& $ibcmd config extension create --database-path=$ib --name=BarcodeTSD --name-prefix=BarcodeTSD_ --purpose=customization
& $ibcmd config import --database-path=$ib --extension=BarcodeTSD "$repo\extension\src"
& $ibcmd config check --database-path=$ib --extension=BarcodeTSD --force
& $ibcmd config apply --database-path=$ib --extension=BarcodeTSD --dynamic=disable --force --session-terminate=force
& $ibcmd extension update --database-path=$ib --name=BarcodeTSD --active=yes
```

## 3. Опубликовать HTTP-сервис

```powershell
$apache = Join-Path $work "apache24"
& "$repo\.codex\skills\1c-metadata-manage\tools\1c-web-ops\scripts\web-publish.ps1" `
  -V8Path "C:\Program Files\1cv8\8.3.27.2130\bin" `
  -InfoBasePath $ib `
  -AppName "RetailTest" `
  -ApachePath $apache `
  -Port 8081
```

Если HTTP-сервис из расширения возвращает `404`, проверьте
`$apache\publish\retailtest\default.vrd`: у `<httpServices>` должен быть
атрибут `publishExtensionsByDefault="true"`.

## 4. Проверить HTTP

Временная ИБ из XML-выгрузки содержит метаданные, но не содержит товарных
данных. Для проверки happy-path создайте один тестовый товар и штрихкод:

```powershell
& "$repo\docs\testing\seed-mvp-data.ps1" `
  -InfoBasePath $ib `
  -Barcode "2000000000035" `
  -Name "Тестовый товар MVP Found"

& "$repo\docs\testing\seed-mvp-data.ps1" `
  -InfoBasePath $ib `
  -Barcode "2000000000042" `
  -Name "Тестовый товар MVP Ambiguous" `
  -Ambiguous
```

```powershell
$url = "http://localhost:8081/retailtest/hs/BarcodeTSD/v1/barcode/resolve"

Invoke-WebRequest -Uri $url -Method Post -ContentType "application/json" `
  -Body '{"barcode":""}' -SkipHttpErrorCheck -UseBasicParsing

Invoke-WebRequest -Uri $url -Method Post -ContentType "application/json" `
  -Body '{"barcode":"0000000000000"}' -SkipHttpErrorCheck -UseBasicParsing

Invoke-WebRequest -Uri $url -Method Post -ContentType "application/json" `
  -Body '{"barcode":"2000000000035"}' -UseBasicParsing

Invoke-WebRequest -Uri $url -Method Post -ContentType "application/json" `
  -Body '{"barcode":"2000000000042"}' -UseBasicParsing
```

Ожидаемо:

- пустой штрихкод -> `400 invalid_request`;
- отсутствующий штрихкод -> `200 not_found`.
- `2000000000035` -> `200 found`, `Тестовый товар MVP Found`.
- `2000000000042` -> `200 ambiguous`, две позиции
  `Тестовый товар MVP Ambiguous`.
