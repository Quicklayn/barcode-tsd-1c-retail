# TSD Product Name Lookup Design

## Architecture Decisions

### SDD workflow

Use OpenSpec only. Spec Kit is intentionally not installed because the project
already has OpenSpec from `ai_rules_1c`, the work is brownfield around a 1C
configuration export, and the first MVP should avoid a second SDD structure.

### Android application

Use native Android Kotlin/Gradle under `android/**`.

- Minimum OS: Android 8.0 Oreo (`minSdk=26`).
- First scanner mode: keyboard wedge input ending with Enter.
- Manual input remains available for emulator and desktop testing.
- The first screen displays only the product name for a successful lookup.
- Vendor-specific scanner intent APIs are deferred until target TSD hardware is
  selected.

### 1C backend

Design the backend extension-first under `extension/**`.

- `RT3/**` remains the read-only baseline export.
- Extension internal name: `BarcodeTSD`.
- HTTP service metadata object name: `BarcodeTSD`.
- Public endpoint: `POST /hs/BarcodeTSD/v1/barcode/resolve`.
- The first endpoint is read-only and must not write documents, registers, or
  settings.
- If metadata validation later proves that the target 1C publication cannot
  expose the HTTP service from an extension, stop implementation and record an
  explicit design amendment before coding the main-configuration fallback.

### API contract

The API contract lives in `docs/api/tsd-api.yaml`.

Initial endpoint:

- `POST /hs/BarcodeTSD/v1/barcode/resolve`
- Request: `{ "barcode": "<raw scanner payload>" }`
- Response statuses: `found`, `not_found`, `ambiguous`.

### Barcode lookup model for MVP

The backend must use only the standard Retail barcode register for this MVP:

- `РегистрСведений.ШтрихкодыНоменклатуры`

Lookup uses the trimmed request barcode as `Штрихкод` and returns distinct
non-empty `Номенклатура` values. For a single distinct item, the backend returns
`status=found`, an opaque item reference, and `Номенклатура.Наименование`. For
more than one distinct item, the backend returns `status=ambiguous` and the
candidate item names. It does not return characteristics, units, packages,
series, prices, balances, or editable document rows.

### Authentication

Use the 1C web publication authentication boundary for MVP. Android stores only
test/dev connection settings while the feature is being built; production
credential storage and MDM provisioning are a later security change.

## Failure Handling

- Empty or whitespace-only barcode after trimming returns HTTP `400`.
- No match returns HTTP `200` with `status=not_found`.
- More than one match returns HTTP `200` with `status=ambiguous` and a list of
  candidate product names.
- Unexpected 1C errors return HTTP `500` with a short diagnostic message and no
  sensitive server details.

## Open Questions

None for the MVP setup. `INFOBASE_PATH` and `INFOBASE_PUBLISH_URL` are runtime
parameters, not design questions; ask for them only when deploy or UI testing is
in scope.

## Context sources

- Local metadata export: `RT3/Configuration.xml` confirms `Розница`
  `3.0.12.261`, `CompatibilityMode=Version8_3_27`.
- Local metadata export: `РегистрСведений.ШтрихкодыНоменклатуры` contains
  `Штрихкод` (Строка 200) and `Номенклатура`
  (`CatalogRef.Номенклатура`).
- Project parameters: `.dev.env` pins `NEW_OBJECTS_IN=extension`,
  `EXTENSION_NAME=BarcodeTSD`, `EXPORT_PATH=RT3`.
- Local BSL export: `РаботаСоШтрихкодами*` and
  `ОборудованиеТерминалыСбораДанных` show existing barcode/TSD patterns.
- 1C MCP tools were not exposed in this session; local XML/BSL export was used
  as the evidence source.
