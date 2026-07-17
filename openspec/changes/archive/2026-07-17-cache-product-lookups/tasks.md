## 1. OpenSpec Planning

- [x] 1.1 Validate `openspec/changes/cache-product-lookups/**` with `npm exec -- openspec validate cache-product-lookups --strict`; planning is complete only when strict validation passes.

## 2. Android Cache Storage

- [x] 2.1 Extend `android/app/src/main/java/ru/local/barcodetsd/BarcodeLookupClient.kt` with an explicit online/cached result source while preserving the existing HTTP result classification; focused JVM tests must pass.
- [x] 2.2 Add `cached_products`, DAO operations, Room database version 2, and an explicit `Migration(1, 2)` in `android/app/src/main/java/ru/local/barcodetsd/CollectionDatabase.kt`; a migration instrumentation test must prove version-1 collection rows survive and the cache table is usable.
- [x] 2.3 Extend `CollectionRepository` so an online result is cached and aggregated atomically, and a normalized barcode can be resolved and aggregated from cache only for the expected active draft; repository instrumentation tests must cover hit, miss, refresh, quantity aggregation, and stale-session rejection.

## 3. Lookup Workflow

- [x] 3.1 Update `android/app/src/main/java/ru/local/barcodetsd/MainActivity.kt` and Android resources so only `LookupResult.ConnectionError` attempts cache fallback, while authentication, not-found, ambiguous, server, and protocol results remain authoritative; the cached success must be visibly marked.
- [x] 3.2 Extend `android/app/src/androidTest/java/ru/local/barcodetsd/MainActivityTest.kt` to verify cached-source presentation and regression behavior for the unchanged online lookup and collection flow.

## 4. Verification And Review

- [x] 4.1 Run Android compilation, JVM tests, lint, debug assembly, and instrumentation-test assembly with the repository Gradle wrapper; every command must pass with `minSdk=26` unchanged.
- [x] 4.2 Run connected Android instrumentation tests on the configured emulator; all migration, repository, and activity tests must pass.
- [x] 4.3 Obtain a read-only `tsd-reviewer` review of the integrated diff, resolve every blocker, and verify `extension/**`, `docs/api/tsd-api.yaml`, credentials, and `RT3/**` have no changes.
- [x] 4.4 Run `pwsh -NoProfile -File .\scripts\quality\Invoke-QualityGate.ps1 -Mode Full -DiffMode Working`; the full repository quality gate and contract checks must pass.

## 5. Spec Synchronization

- [x] 5.1 Archive `cache-product-lookups` with OpenSpec after all implementation tasks pass, synchronize the delta into canonical specs, and validate the archived change and canonical specs strictly.
