## Why

The backend already returns every matching `itemRef` and product name for an ambiguous barcode, but the Android app discards the references and leaves the operator unable to continue the collection. Allowing an explicit candidate choice closes that dead end without introducing a new backend operation.

## What Changes

- Preserve complete product candidates from an online `ambiguous` lookup result.
- Present those candidates as a modal operator choice and add the selected product to the current draft.
- Aggregate repeated selections through the existing expected-session transaction path.
- Keep cancellation non-mutating and restore scanner focus afterward.
- Never cache an operator-selected ambiguous mapping because the barcode remains non-unique in 1C.
- Keep the 1C extension, OpenAPI contract, Room schema, dependencies, credentials, and `RT3/**` unchanged.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `tsd-product-lookup`: replace the display-only ambiguous result with an explicit candidate-selection flow using the existing response fields.
- `barcode-collection-session`: allow an operator-selected online candidate to aggregate into the active draft without populating the product cache.

## Impact

- Android only: lookup result parsing, collection repository orchestration, candidate dialog, strings, and focused tests under `android/**`.
- No API compatibility or 1C metadata/code change is required; `docs/api/tsd-api.yaml`, `extension/**`, and `RT3/**` remain untouched.
- Rollback is the Android commit for this change; no database downgrade or data migration is involved.

## Context sources

- Canonical specs: `tsd-product-lookup` defines the current display-only ambiguous state; `barcode-collection-session` defines aggregation by `itemRef` and expected-session persistence.
- Local contract and code: `docs/api/tsd-api.yaml` already requires `itemRef` and `name` for every `ProductMatch`; `BarcodeLookupClient.kt` currently discards ambiguous references; `CollectionDatabase.kt` exposes transactional draft updates.
- 1C MCP checks skipped: this change makes no new 1C metadata, platform API, or backend behavior claim and does not edit 1C sources.
