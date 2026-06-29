# Android TSD Application

Минимальное native Kotlin/Gradle Android-приложение для MVP TSD.

## Решения MVP

- `minSdk=26` для Android 8.0 Oreo.
- UI: один `Activity` без Compose, AppCompat и внешних UI-фреймворков.
- Сканер: keyboard wedge, отправка lookup по Enter.
- Ручной ввод: то же поле и кнопка `Найти`.
- API: `POST /v1/barcode/resolve` относительно URL сервиса
  `.../hs/BarcodeTSD`.
- Сеть: `HttpURLConnection`, JSON через `org.json`, Basic Auth опционально.
- URL по умолчанию `http://10.0.2.2:8081/retailtest/hs/BarcodeTSD` подходит для
  Android Emulator при локальной публикации 1С на
  `http://localhost:8081/retailtest/hs/BarcodeTSD`; на физическом ТСД укажите
  реальный адрес web-публикации 1С.
- Offline, документы, остатки, цены, DataMatrix и vendor SDK вне MVP.

## Сборка

Проект открывается из каталога `android/` в Android Studio или собирается
Gradle-командой:

```powershell
.\gradlew.bat :app:assembleDebug
```

Быстрые проверки Android-клиента:

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
```

Полный локальный набор, совпадающий с GitHub Actions:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --no-daemon --no-configuration-cache
```

Требования выбранной связки: JDK 17, Android SDK API 36. Gradle запускается
через wrapper из каталога `android/`.

Текущий package/application id для MVP: `ru.local.barcodetsd`.

## Запуск на эмуляторе или ТСД

```powershell
.\gradlew.bat :app:installDebug
adb shell am start -n ru.local.barcodetsd/.MainActivity
```

На эмуляторе для обращения к web-публикации на локальном ПК используйте
`10.0.2.2` вместо `localhost`, сохраняя порт и путь публикации. Например:
`http://localhost:8081/retailtest/hs/BarcodeTSD` на ПК соответствует
`http://10.0.2.2:8081/retailtest/hs/BarcodeTSD` в приложении на эмуляторе.
Если debug-приложение уже запускалось со старым URL, измените поле URL вручную
или очистите данные приложения.

Для автоматизированного smoke после `run-mvp-smoke.ps1 -KeepWebServer`:

```powershell
..\docs\testing\run-android-smoke.ps1
```
