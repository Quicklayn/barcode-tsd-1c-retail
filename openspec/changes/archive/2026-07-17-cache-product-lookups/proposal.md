## Why

The current collection workflow cannot continue when the TSD temporarily loses connectivity, even for products that the same device resolved successfully before. A small local cache removes that repeated network dependency without introducing full catalog synchronization.

## What Changes

- Persist every unambiguous online product lookup in the existing Room database.
- On a network connection failure, resolve the normalized barcode from that cache and continue the same collection aggregation flow when a cached product exists.
- Mark cached results explicitly in the UI so the operator knows 1C was not contacted.
- Preserve current sessions through a Room schema migration from version 1 to version 2.
- Keep authentication failures, ambiguous/not-found responses, server/protocol errors, full catalog synchronization, cache management UI, TTL/eviction, and background refresh outside this change.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `tsd-product-lookup`: replace the online-only failure behavior with a narrow cached fallback for connection failures.
- `barcode-collection-session`: allow an unambiguous cached result to aggregate into the active draft while preserving all existing draft and submission rules.

## Impact

- Android only: Room schema/entity/DAO/migration, lookup orchestration, result presentation, and Android tests under `android/**`.
- The 1C extension, `docs/api/tsd-api.yaml`, credentials, and `RT3/**` remain unchanged.
- Rollback is the Android commit for this change; the version-2 database remains backward-incompatible with an older APK, so rollback testing must clear app data or reinstall the previous APK.

## Context sources

- Canonical specs: `tsd-product-lookup` defines the online-only baseline; `barcode-collection-session` defines scan aggregation and durable Room state.
- Local Android code: `BarcodeLookupClient.kt`, `CollectionDatabase.kt`, and `MainActivity.kt` confirm the current result types, Room version 1, and single lookup pipeline.
- 1C MCP checks skipped: this change does not alter or make new claims about 1C metadata, APIs, or behavior.
