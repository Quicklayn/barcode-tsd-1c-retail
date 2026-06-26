# TSD Product Name Lookup

## Requirements

### Requirement: Online Product Name Lookup

The system MUST allow an Android TSD user to submit one scanned barcode and
receive the matching product name from 1C:Retail without writing business data.

#### Scenario: Product barcode is found

- GIVEN the Android TSD has network access to the 1C HTTP publication
- AND the barcode exists in standard Retail barcode data
- WHEN the user scans the barcode
- THEN the app receives `status=found`
- AND the app displays `Номенклатура.Наименование`
- AND the response includes the matched 1C item reference.

#### Scenario: Product barcode is not found

- GIVEN the Android TSD has network access to the 1C HTTP publication
- AND the barcode does not exist in standard Retail barcode data
- WHEN the user scans the barcode
- THEN the app receives `status=not_found`
- AND the app displays a not-found state without creating any 1C data.

#### Scenario: Product barcode has multiple matches

- GIVEN the Android TSD has network access to the 1C HTTP publication
- AND the barcode resolves to more than one product presentation
- WHEN the user scans the barcode
- THEN the app receives `status=ambiguous`
- AND the response includes all candidate item names returned by 1C
- AND the app displays the ambiguity instead of silently choosing one item.

### Requirement: Standard Retail Barcode Register

The 1C backend MUST resolve product barcodes for this MVP using
`РегистрСведений.ШтрихкодыНоменклатуры`.

#### Scenario: Barcode register lookup

- GIVEN a barcode exists in `РегистрСведений.ШтрихкодыНоменклатуры`
- WHEN the backend resolves the barcode
- THEN the backend searches by the `Штрихкод` dimension
- AND returns the distinct linked `Номенклатура` items.

#### Scenario: Package barcode is outside MVP

- GIVEN a barcode exists only in `Справочник.ШтрихкодыУпаковокТоваров`
- WHEN the backend resolves the barcode
- THEN the backend does not use that catalog in this MVP.

### Requirement: Android Scanner Baseline

The Android MVP MUST support scanner input through keyboard wedge mode and MUST
also allow manual input for testing.

#### Scenario: Scanner sends Enter after barcode

- GIVEN the lookup screen is active
- WHEN the scanner types a barcode and sends Enter
- THEN the app sends the barcode to the 1C lookup endpoint.

#### Scenario: Manual input is used

- GIVEN the lookup screen is active
- WHEN the user manually enters a barcode and submits it
- THEN the app sends the barcode to the 1C lookup endpoint.

### Requirement: Online-Only MVP

The MVP MUST be online-only and MUST NOT use an offline product cache.

#### Scenario: 1C server is unavailable

- GIVEN the Android TSD cannot reach the 1C HTTP publication
- WHEN the user scans a barcode
- THEN the app displays a connection error state
- AND the app does not attempt offline lookup.

### Requirement: Minimal Result Surface

The Android MVP MUST display only the product name for a successful lookup.

#### Scenario: Successful lookup returns extra backend fields

- GIVEN the backend response includes an item reference for future operations
- WHEN the app displays the successful result
- THEN the visible product result contains the product name
- AND the app does not display stock, price, package, characteristic, or series
  data.

## Context sources

- Local metadata export: `РегистрСведений.ШтрихкодыНоменклатуры` confirms
  `Штрихкод` (Строка 200) and `Номенклатура`
  (`CatalogRef.Номенклатура`).
- Local BSL export: `РаботаСоШтрихкодами*` and
  `ОборудованиеТерминалыСбораДанных` confirm existing barcode/TSD patterns.
- `docs/api/tsd-api.yaml` defines the minimal API contract shape.
- 1C MCP tools were not exposed during implementation; local XML/BSL export was
  used as the evidence source.
