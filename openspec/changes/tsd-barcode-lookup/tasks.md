# TSD Product Name Lookup Tasks

## 1. Project Foundation

- [x] Initialize git repository.
- [x] Add `.gitignore` for `.dev.env`, 1C binaries/logs, and Android build
  outputs.
- [x] Record mixed 1C/Android project rules in `USER-RULES.md`.
- [x] Refresh `openspec/project.md` from `RT3/`.
- [x] Add minimal API contract in `docs/api/tsd-api.yaml`.
- [x] Add workspace placeholders for `android/` and `extension/`.

## 2. 1C Backend

- [x] Create the `BarcodeTSD` extension workspace.
- [x] Add a 1C HTTP service `BarcodeTSD` with endpoint
  `POST /hs/BarcodeTSD/v1/barcode/resolve`.
- [x] Implement read-only lookup in `РегистрСведений.ШтрихкодыНоменклатуры`
  by trimmed `Штрихкод`.
- [x] Return `found`, `not_found`, and `ambiguous` statuses.
- [x] Return only item reference and `Номенклатура.Наименование` for product
  candidates.
- [x] Validate BSL and metadata XML.

## 3. Android MVP

- [x] Create native Kotlin/Gradle Android project under `android/`.
- [x] Configure `minSdk=26`.
- [x] Add a single lookup screen with scan/manual input.
- [x] Trigger lookup on scanner Enter event.
- [x] Display the product name for `found`.
- [x] Display simple not-found, ambiguous, invalid-input, auth, server, and
  connection-error states.
- [x] Add network error and authentication error states.

## 4. Integration Verification

- [ ] Fill `INFOBASE_PATH` when deploy/load-to-IB is in scope.
- [ ] Fill `INFOBASE_PUBLISH_URL` when HTTP/UI smoke testing is in scope.
- [x] Deploy backend to a temporary test infobase created from `RT3/`.
- [ ] Run one happy-path barcode lookup against a barcode from
  `РегистрСведений.ШтрихкодыНоменклатуры`.
- [x] Run no-match and invalid-input HTTP checks against the temporary
  infobase.
- [ ] Run ambiguous-match check against an infobase with seeded duplicate
  barcodes.
- [x] Run the Android app on emulator or TSD hardware with manual input.

## Context sources

- Local metadata export: `RT3/Configuration.xml`, `RT3/InformationRegisters`,
  `RT3/Catalogs`, `RT3/CommonModules`.
- `openspec/project.md` for project-wide context.
- `docs/api/tsd-api.yaml` for the minimal API contract.
- 1C MCP tools were not exposed in this session; local XML/BSL export was used
  as the evidence source.
