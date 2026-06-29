# MVP Acceptance Checklist

Этот чеклист фиксирует минимальную приемку первого вертикального MVP:
сканирование штрихкода на Android ТСД и отображение наименования номенклатуры
из 1С.

## 1. Backend smoke

На машине разработчика:

```powershell
.\docs\testing\run-mvp-smoke.ps1
```

Если путь платформы отличается:

```powershell
.\docs\testing\run-mvp-smoke.ps1 -V8Path "C:\Program Files\1cv8\8.3.27.2130\bin"
```

Ожидаемый результат: `MVP smoke passed`.

## 2. Публикация реальной ИБ

Для реального стенда опубликуйте ИБ с HTTP-сервисами расширений. В `default.vrd`
для публикации должен быть включен атрибут:

```xml
publishExtensionsByDefault="true"
```

Рабочий URL для приложения имеет вид:

```text
http://server/infobase/hs/BarcodeTSD
```

Android-клиент сам добавляет путь `/v1/barcode/resolve`.

## 3. Пользователь 1С для ТСД

Для MVP можно использовать существующего технического пользователя 1С. Он должен
успешно аутентифицироваться в опубликованной ИБ. В правах 1С назначьте ему роль
расширения `BarcodeTSD_Use`.

Роль дает минимальные права на:

- вызов HTTP-сервиса `BarcodeTSD`;
- чтение `РегистрСведений.ШтрихкодыНоменклатуры`;
- чтение `Справочник.Номенклатура`.

Если приложение получает `401` или `403`, сначала проверьте пользователя,
пароль и права этого пользователя в ИБ.

Минимальная проверка роли на стенде:

```powershell
curl.exe -i -u "tsd_user:password" -X POST `
  -H "Content-Type: application/json" `
  --data '{"barcode":"0000000000000"}' `
  "http://server/infobase/hs/BarcodeTSD/v1/barcode/resolve"
```

Ожидаемый результат для неизвестного штрихкода - `200 not_found`. Ответ `401`
или `403` означает, что пользователь не прошел аутентификацию или ему не хватает
роли `BarcodeTSD_Use`.

## 4. Android verification

В каталоге `android/`:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --no-daemon --no-configuration-cache
```

В GitHub Actions debug APK сохраняется как artifact `barcode-tsd-debug-apk`.
Отчеты `lintDebug` и `testDebugUnitTest` сохраняются как
`android-verification-reports`.

Установить debug APK:

```powershell
.\gradlew.bat :app:installDebug
adb shell am start -n ru.local.barcodetsd/.MainActivity
```

Для Android Emulator при публикации на локальном ПК используйте URL:

```text
http://10.0.2.2:8081/retailtest/hs/BarcodeTSD
```

Для физического ТСД используйте сетевой адрес сервера 1С, доступный с ТСД.

Автоматизированный smoke на подключенном эмуляторе или ТСД:

```powershell
.\docs\testing\run-mvp-smoke.ps1 -KeepWebServer
.\docs\testing\run-android-smoke.ps1
```

То же одной командой с автоматическим определением порта публикации:

```powershell
.\docs\testing\run-full-mvp-smoke.ps1
```

Этот smoke проверяет отображение найденного товара, состояние `Не найдено` и
неоднозначный список.

Для физического ТСД передайте реальный URL публикации:

```powershell
.\docs\testing\run-android-smoke.ps1 -ServiceUrl "http://server/infobase/hs/BarcodeTSD"
```

## 5. Ручная приемка

Проверить в приложении:

- пустой штрихкод не отправляется, отображается ошибка ввода;
- неизвестный штрихкод показывает `Не найдено`;
- существующий штрихкод показывает только наименование товара;
- неоднозначный штрихкод показывает список найденных наименований;
- повторный Enter во время запроса не запускает второй запрос;
- неверный URL или недоступная публикация показывают ошибку подключения.
