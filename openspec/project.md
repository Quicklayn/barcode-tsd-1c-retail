# Project Context

> Generated from the local `RT3/` 1C XML export and project setup decisions.
> Keep this file as stable context for OpenSpec work; refresh it when the
> baseline export or architecture changes materially.

## Configuration

- Name: `Розница`
- Synonym: `Розница, редакция 3.0`
- Vendor: `Фирма "1С"`
- Version: `3.0.12.261`
- CompatibilityMode: `Version8_3_27` (`8.3.27`)
- Default run mode: `ManagedApplication`
- Form mode: managed forms are enabled; ordinary forms in managed application are disabled
- Project kind: 1C:Retail 3.0 baseline export plus native Android TSD application workspace

## Standard Subsystems Library

- BSP/SSL detected: `СтандартныеПодсистемы` subsystem exists in `RT3/Subsystems`
- Version: not detected from the current local export scan

## Top-Level Subsystems

- `АдминистрированиеСервиса`
- `ГосИС`
- `Деньги`
- `Закупки`
- `ИнтернетПоддержкаПользователей`
- `Компания`
- `МобильноеПриложениеУНФ`
- `НормативноСправочнаяИнформация`
- `ОбменСКассовымСерверомШтрихМ`
- `Онлайн`
- `ПоддержкаОборудования`
- `Продажи`
- `РМК`
- `Розница`
- `Склад`
- `СтандартныеПодсистемы`
- `УправлениеМобильнымиПриложениями`

## Metadata Counts

- Catalogs: 702
- Documents: 343
- Information registers: 899
- Accumulation registers: 127
- Accounting registers: 1
- Calculation registers: 2
- Common modules: 3289
- Reports: 353
- Data processors: 352
- HTTP services: 17

## TSD MVP Context

- The first target capability is Android TSD barcode lookup: scan a product
  barcode and display the matching item name.
- Android devices must support Android 8.0 Oreo or newer (`minSdk=26`).
- The Android application is native Kotlin/Gradle and lives under `android/**`.
- The 1C integration is extension-first and lives under `extension/**`.
- API contracts live under `docs/api/**`.
- The accepted SDD workflow is OpenSpec only; Spec Kit is intentionally not used.

## Key 1C Objects For Barcode Lookup

- `Справочник.Номенклатура`
- `РегистрСведений.ШтрихкодыНоменклатуры`
  - `Штрихкод`
  - `Номенклатура`
  - `Характеристика`
  - `Партия`
  - `ЕдиницаИзмерения`
- `Справочник.ШтрихкодыУпаковокТоваров`
  - `ЗначениеШтрихкода`
  - `Номенклатура`
  - `Характеристика`
  - `Упаковка`
  - `Серия`
  - `Количество`
- `ОбщийМодуль.РаботаСоШтрихкодами`
- `ОбщийМодуль.РаботаСоШтрихкодамиУНФ`
- `ОбщийМодуль.ОборудованиеТерминалыСбораДанных`

## Operational Parameters

- `.dev.env` is the source of truth for platform path, infobase connection,
  export path, and extension placement policy.
- Current known values:
  - `PLATFORM_VERSION=8.3.27`
  - `NEW_OBJECTS_IN=extension`
  - `EXTENSION_NAME=BarcodeTSD`
  - `EXPORT_PATH=RT3`
- `INFOBASE_PATH` and `INFOBASE_PUBLISH_URL` are still intentionally empty;
  request them only when an IB-bound deploy/test task is in scope.

## Context Sources

- `RT3/Configuration.xml`: configuration name, synonym, vendor, version,
  compatibility mode, run mode, form mode.
- `RT3/Subsystems/*`: subsystem names and BSP/SSL subsystem presence.
- `RT3/*` directory counts: metadata counts.
- `RT3/InformationRegisters/ШтрихкодыНоменклатуры.xml`: barcode register
  dimensions.
- `RT3/Catalogs/ШтрихкодыУпаковокТоваров.xml`: package barcode catalog
  attributes.
- `RT3/CommonModules/РаботаСоШтрихкодами/Ext/Module.bsl`,
  `RT3/CommonModules/РаботаСоШтрихкодамиУНФ/Ext/Module.bsl`,
  `RT3/CommonModules/ОборудованиеТерминалыСбораДанных/Ext/Module.bsl`:
  existing barcode and TSD patterns.
