# Barcode TSD for 1C:Retail

MVP мобильного приложения для ТСД на Android и расширения 1С:Розница 3.0.

## Цель MVP

- считать или ввести штрихкод номенклатуры на ТСД;
- отправить его в 1С через HTTP-сервис расширения;
- показать наименование найденного товара.

## Состав репозитория

- `android/` — native Kotlin/Gradle Android-приложение, `minSdk=26`;
- `extension/` — расширение 1С `BarcodeTSD` с HTTP-сервисом;
- `docs/api/` — OpenAPI-контракт между Android и 1С;
- `openspec/` — спецификации OpenSpec и архив изменений;
- `.codex/` — правила, навыки и субагенты для AI-разработки 1С.

## Важное про `RT3/`

Локальная папка `RT3/` используется как read-only выгрузка типовой
конфигурации 1С:Розница 3.0 для анализа метаданных. Она не публикуется в
репозитории, потому что содержит исходники типовой конфигурации 1С.

Для продолжения разработки подготовьте `RT3/` локально из своей тестовой базы.

## Текущий статус

Реализован первый вертикальный MVP:

- `POST /hs/BarcodeTSD/v1/barcode/resolve`;
- поиск по `РегистрСведений.ШтрихкодыНоменклатуры`;
- статусы `found`, `not_found`, `ambiguous`;
- Android-экран ввода/сканирования штрихкода.

## Разработка через OpenSpec

OpenSpec является единственным SDD-процессом проекта. Перед первой работой
из корня репозитория выполните:

```powershell
.\scripts\setup\Initialize-Development.ps1
```

Команда устанавливает зафиксированную версию OpenSpec, подключает Git hooks и
запускает быстрые проверки. Полный цикл `propose → apply → verify → archive`,
режимы quality gate, CI, обновление правил и восстановление окружения описаны в
[`docs/development/openspec-workflow.md`](docs/development/openspec-workflow.md).

Локально проверено:

- Android build/install/run на эмуляторе;
- Android happy-path через mock HTTP;
- Android JVM unit tests для `BarcodeLookupClient`;
- временная файловая ИБ из `RT3/`;
- импорт, проверка, применение и активация расширения `BarcodeTSD`;
- web-публикация HTTP-сервиса из расширения;
- live HTTP smoke для `invalid_request`, `not_found`, `found` и `ambiguous`.

## Быстрая проверка Android

```powershell
cd android
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
.\gradlew.bat :app:installDebug
adb shell am start -n ru.local.barcodetsd/.MainActivity
```

Единый полный quality gate, эквивалентный обязательным локальным проверкам:

```powershell
.\scripts\quality\Invoke-QualityGate.ps1 -Mode Full -DiffMode Working
```

APK после сборки: `android/app/build/outputs/apk/debug/app-debug.apk`.

GitHub Actions workflow `Quality Gate` проверяет OpenSpec, PowerShell,
структуру расширения 1С, межстековый контракт и Android на каждом pull request
и push в `main`. APK и отчеты Android сохраняются как artifacts.

Для эмулятора URL по умолчанию:
`http://10.0.2.2:8081/retailtest/hs/BarcodeTSD`. Для физического ТСД укажите в
приложении реальный адрес web-публикации 1С.

## Проверка полного MVP

Полный сценарий с временной ИБ, публикацией HTTP-сервиса и тестовым штрихкодом
описан в `docs/testing/mvp-smoke.md`.
Приемочный чеклист для реального стенда: `docs/deployment/mvp-acceptance.md`.

Быстрый backend smoke-прогон:

```powershell
.\docs\testing\run-mvp-smoke.ps1
```

Если платформа 1С установлена в другой каталог, передайте путь явно:

```powershell
.\docs\testing\run-mvp-smoke.ps1 -V8Path "C:\Program Files\1cv8\8.3.27.2130\bin"
```

Backend + Android smoke на эмуляторе:

```powershell
.\docs\testing\run-mvp-smoke.ps1 -KeepWebServer
.\docs\testing\run-android-smoke.ps1
```

Android-smoke проверяет UI-сценарии `found`, `not_found` и `ambiguous`.

Полный автоматический smoke одной командой:

```powershell
.\docs\testing\run-full-mvp-smoke.ps1
```

Тот же сценарий вместе со всеми предварительными проверками:

```powershell
.\scripts\quality\Invoke-QualityGate.ps1 -Mode Mvp -DiffMode Working
```
