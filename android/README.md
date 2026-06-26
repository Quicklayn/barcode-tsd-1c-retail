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
- URL по умолчанию `http://10.0.2.2/RetailTest/hs/BarcodeTSD` подходит для
  Android Emulator; на физическом ТСД укажите реальный адрес web-публикации 1С.
- Offline, документы, остатки, цены, DataMatrix и vendor SDK вне MVP.

## Сборка

Проект открывается из каталога `android/` в Android Studio или собирается
Gradle-командой:

```powershell
.\gradlew.bat :app:assembleDebug
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
`10.0.2.2` вместо `localhost`.
