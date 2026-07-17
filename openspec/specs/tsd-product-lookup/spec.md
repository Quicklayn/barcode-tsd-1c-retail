# TSD Product Name Lookup

## Purpose

Define the MVP behavior for online barcode lookup from an Android TSD against
the 1C:Retail backend, limited to resolving and displaying the product name.
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

### Requirement: Barcode Request Validation

The 1C backend MUST accept only the minimal barcode lookup request shape
defined by `docs/api/tsd-api.yaml`.

#### Scenario: Request contains extra fields

- GIVEN a request JSON object contains `barcode`
- AND the same request contains any extra top-level field
- WHEN the backend validates the request
- THEN the backend rejects it with `400 invalid_request`.

#### Scenario: Request contains malformed JSON

- GIVEN a request body is not valid JSON
- WHEN the backend validates the request
- THEN the backend rejects it with `400 invalid_request`.

#### Scenario: Barcode is too long

- GIVEN a request contains a `barcode` longer than 200 characters
- WHEN the backend validates the request
- THEN the backend rejects it with `400 invalid_request`.

### Requirement: TSD Access Role

The 1C extension MUST provide a minimal role for the TSD technical user.

#### Scenario: Technical user has MVP lookup access

- GIVEN the role `BarcodeTSD_Use` is assigned to the TSD technical user
- WHEN the user calls the barcode lookup HTTP service
- THEN the role allows use of `HTTPService.BarcodeTSD`
- AND the role allows read access to `РегистрСведений.ШтрихкодыНоменклатуры`
- AND the role allows read access to `Справочник.Номенклатура`.

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

### Requirement: Minimal Result Surface

The Android MVP MUST display only the product name for a successful lookup.

#### Scenario: Successful lookup returns extra backend fields

- GIVEN the backend response includes an item reference for future operations
- WHEN the app displays the successful result
- THEN the visible product result contains the product name
- AND the app does not display stock, price, package, characteristic, or series
  data.

### Requirement: Successful Product Lookup Cache
The Android application MUST persist the normalized barcode, 1C `itemRef`, and product name after every unambiguous successful online lookup and MUST replace the cached row when a later successful lookup resolves the same barcode.

#### Scenario: Online product is cached
- **WHEN** 1C returns one `found` product for a normalized barcode
- **THEN** the application persists that barcode, `itemRef`, and product name before presenting the successful result

#### Scenario: Online product mapping changes
- **WHEN** a later successful online lookup returns different product data for an already cached barcode
- **THEN** the application replaces the cached product data with the latest successful response

### Requirement: Cached Connection-Failure Fallback
The Android application MUST attempt local product lookup only after a network connection failure and MUST keep all authoritative 1C responses visible without cache substitution.

#### Scenario: Connection fails and barcode is cached
- **WHEN** the online request ends with a connection failure and the normalized barcode exists in the local cache
- **THEN** the application returns the cached `itemRef` and product name as a successful result marked as cached

#### Scenario: Connection fails and barcode is not cached
- **WHEN** the online request ends with a connection failure and the normalized barcode is absent from the local cache
- **THEN** the application displays the original connection error and does not report a product

#### Scenario: Authoritative response is not successful
- **WHEN** 1C returns `not_found`, `ambiguous`, an authentication error, or a server/protocol error
- **THEN** the application displays that result and does not replace it with cached data

### Requirement: Durable Compatible Cache Storage
The Android application MUST retain cached products across process restarts and MUST migrate an existing version-1 Room database to the cache-enabled schema without losing the active collection session or its lines.

#### Scenario: Application restarts after caching a product
- **WHEN** a cached product exists and the application process restarts
- **THEN** the same barcode can be resolved from the local cache after a connection failure

#### Scenario: Existing collection database is upgraded
- **WHEN** the application first opens an existing version-1 database after the update
- **THEN** Room migrates it to version 2 and preserves the session header, state, lines, quantities, and document reference

#### Scenario: Cached product has not been refreshed
- **WHEN** no later successful online lookup occurs for a cached barcode
- **THEN** the application retains the cached row until application data is cleared

## Context sources

- Local metadata export: `РегистрСведений.ШтрихкодыНоменклатуры` confirms
  `Штрихкод` (Строка 200) and `Номенклатура`
  (`CatalogRef.Номенклатура`).
- Local extension metadata: `extension/src/Roles/BarcodeTSD_Use/Ext/Rights.xml`
  defines the minimal TSD HTTP lookup role.
- Local BSL export: `РаботаСоШтрихкодами*` and
  `ОборудованиеТерминалыСбораДанных` confirm existing barcode/TSD patterns.
- `docs/api/tsd-api.yaml` defines the minimal API contract shape.
- 1C MCP tools were not exposed during implementation; local XML/BSL export was
  used as the evidence source.
