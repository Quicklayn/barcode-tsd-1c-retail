# TSD Product Name Lookup Proposal

## Why

TSD users need the smallest useful mobile workflow: scan a product barcode on an
Android terminal and immediately see the product name from 1C:Retail. This
creates the first vertical slice without documents, stock, prices, or offline
sync.

## What Changes

- Add a project API contract for resolving a scanned barcode to a product name.
- Add a 1C backend endpoint, designed extension-first, that performs a read-only
  lookup against `РегистрСведений.ШтрихкодыНоменклатуры`.
- Add a native Android MVP screen that accepts a scanner input and displays the
  product name or a simple non-success state.
- Keep the first version online-only.

## Scope

Included:

- Product barcodes stored in `РегистрСведений.ШтрихкодыНоменклатуры`.
- Displaying `Номенклатура.Наименование` for a single matching product.
- Multiple-product, no-match, invalid-input, authentication, connection, and
  server-error states.
- Keyboard wedge scanner mode and manual input for Android testing.

Excluded from this MVP:

- Offline product cache.
- Creating or editing 1C documents.
- Stock balances, prices, batches, inventory, and acceptance operations.
- Package barcodes from `Справочник.ШтрихкодыУпаковокТоваров`.
- Characteristics, units, packages, and series in the Android result view.
- Vendor-specific scanner SDKs or intent integrations.
- Marking validation and GS1/DataMatrix business processing.

## Approach

Use the existing OpenSpec workspace. Implement the MVP as one vertical slice:
Android scan input → HTTP request → 1C read-only barcode lookup → Android result
display of the product name.

## Context sources

- Local metadata export: `RT3/Configuration.xml` confirms `Розница`
  `3.0.12.261`, `CompatibilityMode=Version8_3_27`.
- Local metadata export: `РегистрСведений.ШтрихкодыНоменклатуры` provides
  `Штрихкод` (Строка 200) and `Номенклатура`
  (`CatalogRef.Номенклатура`).
- Local BSL export: `РаботаСоШтрихкодами*` and
  `ОборудованиеТерминалыСбораДанных` show existing barcode/TSD patterns.
- 1C MCP tools were not exposed in this session; local XML/BSL export was used
  as the evidence source.
