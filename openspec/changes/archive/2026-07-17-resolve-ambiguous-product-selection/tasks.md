## 1. OpenSpec Planning

- [x] 1.1 Validate `openspec/changes/resolve-ambiguous-product-selection/**` with `npm exec -- openspec validate resolve-ambiguous-product-selection --strict`; implementation starts only after strict validation passes.

## 2. Android Candidate Model And Persistence

- [x] 2.1 Update `android/app/src/main/java/ru/local/barcodetsd/BarcodeLookupClient.kt` so `LookupResult.Ambiguous` retains ordered `itemRef`/name candidates and rejects incomplete candidate payloads; focused JVM tests must pass.
- [x] 2.2 Add a non-caching expected-session aggregation path to `CollectionRepository` in `android/app/src/main/java/ru/local/barcodetsd/CollectionDatabase.kt`; instrumentation tests must prove selected candidates aggregate, stale sessions are rejected, and `cached_products` is not created or replaced by selection.

## 3. Android Selection Workflow

- [x] 3.1 Update `android/app/src/main/java/ru/local/barcodetsd/MainActivity.kt` and Android strings to present ordered ambiguous candidates in a cancellable modal list, persist the exact selected candidate, and restore scanner focus after cancellation or completion.
- [x] 3.2 Extend `android/app/src/androidTest/java/ru/local/barcodetsd/MainActivityTest.kt` to verify candidate presentation, exact selection, cancellation without mutation, repeated aggregation, and unchanged online/cached lookup behavior.

## 4. Verification And Review

- [x] 4.1 Run Android JVM tests, lint, debug assembly, and instrumentation-test assembly with the repository Gradle wrapper; all commands must pass with dependencies and `minSdk=26` unchanged.
- [x] 4.2 Run connected Android instrumentation tests on `BarcodeTSD_API36`; all parser, repository, dialog, and regression tests must pass.
- [x] 4.3 Obtain a read-only `tsd-reviewer` review, resolve every blocker, and verify `extension/**`, `docs/api/tsd-api.yaml`, Room schema/version, credentials, and `RT3/**` have no changes.
- [x] 4.4 Run `pwsh -NoProfile -File .\scripts\quality\Invoke-QualityGate.ps1 -Mode Full -DiffMode Working`; the full repository gate and contract checks must pass.

## 5. Spec Synchronization

- [x] 5.1 Archive `resolve-ambiguous-product-selection` with OpenSpec after all implementation tasks pass, synchronize both modified capabilities, and strictly validate the archived/canonical state.
