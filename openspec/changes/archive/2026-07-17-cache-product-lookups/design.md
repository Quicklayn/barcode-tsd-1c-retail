## Context

The Android app currently performs every product lookup online, then applies a successful `LookupResult.Found` to the durable Room-backed collection session. Room schema version 1 stores only the session header and lines. Temporary network loss therefore blocks repeated work even when the device resolved the same barcode earlier.

This change is Android-only, keeps `minSdk=26`, and preserves the current 1C/OpenAPI contract. Cached names and references are advisory local data; 1C remains authoritative and validates every submitted `itemRef`.

## Goals / Non-Goals

**Goals:**

- Cache successful unambiguous product lookups incrementally.
- Continue scan aggregation for cached products when the HTTP request cannot connect.
- Preserve existing collection data through an explicit Room migration.
- Keep cached and online results on the same scanner/manual-input pipeline.

**Non-Goals:**

- Full or incremental catalog synchronization from 1C.
- Background refresh, WorkManager, TTL, eviction, or a cache-management screen.
- Offline substitution for authentication, not-found, ambiguous, server, or protocol responses.
- Any change to the 1C extension, API contract, credentials, or `RT3/**`.

## Decisions

### 1. Add one Room cache table and migrate version 1 to version 2

Add `cached_products` keyed by normalized barcode with `itemRef` and product name. Upgrade `BarcodeDatabase` to version 2 with an explicit `Migration(1, 2)` that only creates the new table, preserving the existing session tables and rows.

Alternative considered: destructive migration. Rejected because losing a completed unsent collection violates the durable-session requirement.

### 2. Cache and aggregate an online result in one Room transaction

Extend the existing Room repository so a successful online `Found` result upserts the cache row and mutates the latest session selected by expected `sessionId` inside one transaction. A lifecycle-stale request cannot overwrite a newer session, and a line is not accepted without the corresponding cache update.

Alternative considered: independent best-effort cache writes. Rejected because process death between writes produces avoidable inconsistency.

### 3. Use cache only for transport connection failures

After `BarcodeLookupClient` returns `ConnectionError`, query `cached_products` by the already normalized barcode. A hit becomes `LookupResult.Found` with an explicit cached-source marker and uses the same aggregation path. A miss keeps the original connection error. All responses received from 1C remain authoritative and bypass fallback.

Alternative considered: fallback on every non-success. Rejected because it could hide revoked access, an explicit not-found result, ambiguous data, or a backend contract defect.

### 4. Retain cached rows without TTL or eviction in this slice

The cache grows only from barcodes scanned successfully on this device. A later successful lookup replaces the row for that barcode. Stale entries remain visibly cached and final 1C submission still validates their `itemRef`.

Alternative considered: TTL and size-based eviction. Deferred until real device storage and catalog-size evidence exists; adding policy now would create settings without a measured requirement.

### 5. Keep UI changes limited to source disclosure

The existing success surface shows a distinct cached status/message while keeping the same product name and collection line behavior. No cache list, counters, or settings are added.

## Risks / Trade-offs

- [A cached name or reference can become stale] -> Mark cached results explicitly; refresh on every later successful online lookup; rely on 1C submission validation.
- [A Room migration error could block startup] -> Add a real version-1 database migration test that verifies session rows and opens schema version 2.
- [A late network callback could race with activity recreation] -> Reuse expected-`sessionId` transactional mutation and add lifecycle/repository regression coverage.
- [Cache fallback could hide a server decision] -> Trigger only for `ConnectionError`, never for a received HTTP/application result.

## Migration Plan

1. Ship the version-2 APK with `Migration(1, 2)` registered before any cache feature is used.
2. On first open, create `cached_products` while retaining current session tables and data.
3. Populate the cache only through later successful scans; no one-time import is required.
4. Roll forward by fixing the migration if a defect appears. Installing an older version-1 APK requires clearing app data because Room downgrades are not supported by this change.

## Open Questions

None.

## Context sources

- Local Android implementation: `CollectionDatabase.kt` (Room v1 and transactional session mutation), `BarcodeLookupClient.kt` (`LookupResult` boundary), and `MainActivity.kt` (single scan pipeline).
- Canonical specs: `tsd-product-lookup` and `barcode-collection-session` define the behavior modified here.
- 1C MCP checks skipped: no 1C metadata, platform API, or backend behavior changes are proposed.
