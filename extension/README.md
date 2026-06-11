# 1C Extension Workspace

This folder is reserved for the 1C extension that will expose the TSD backend
API without modifying the `RT3/` baseline export directly.

## Baseline Decisions

- Extension internal name: `BarcodeTSD`.
- New 1C development is extension-first.
- `RT3/` remains the read-only source of facts about the standard Retail 3.0
  configuration.
- HTTP service metadata object name: `BarcodeTSD`.
- HTTP endpoint: `POST /hs/BarcodeTSD/v1/barcode/resolve`.
- The first backend capability is a read-only HTTP endpoint for product barcode
  lookup.
- The endpoint must use standard Retail barcode metadata for the MVP:
  - `РегистрСведений.ШтрихкодыНоменклатуры`
- The first response returns only the product name and an opaque item reference.

## Deferred Until Implementation

- Whether the target platform/publication accepts the HTTP service from an
  extension. If not, the same API contract will be implemented in the main
  configuration as an explicit fallback decision.
