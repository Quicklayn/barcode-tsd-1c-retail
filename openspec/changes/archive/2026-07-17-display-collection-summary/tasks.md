## 1. OpenSpec Planning

- [x] 1.1 Validate `openspec/changes/display-collection-summary/**` with `npm exec -- openspec validate display-collection-summary --strict`; implementation starts only after strict validation passes.

## 2. Android Summary Implementation

- [x] 2.1 Update `android/app/src/main/java/ru/local/barcodetsd/MainActivity.kt`, `android/app/src/main/res/values/ids.xml`, and `android/app/src/main/res/values/strings.xml` to render `Позиций: N · Количество: X` from the current session above the line list, with exact compact decimal formatting and no independent state.
- [x] 2.2 Add focused Android tests proving zero, whole, fractional, repeated-scan, edit/delete, restore, and immutable-state summary behavior without changing Room or network models.

## 3. Verification And Review

- [x] 3.1 Run Android JVM tests, lint, debug assembly, and instrumentation-test assembly with the repository Gradle wrapper; all commands must pass with dependencies, Room `version=2`, and `minSdk=26` unchanged.
- [x] 3.2 Run connected Android instrumentation tests on `BarcodeTSD_API36`; all summary and regression tests must pass.
- [x] 3.3 Obtain a read-only `tsd-reviewer` review, resolve every blocker, and verify `extension/**`, `docs/api/**`, Room schema/version, Gradle dependencies, credentials, and `RT3/**` have no changes.
- [x] 3.4 Run `pwsh -NoProfile -File .\scripts\quality\Invoke-QualityGate.ps1 -Mode Full -DiffMode Working`; the full repository gate and OpenSpec coverage must pass.

## 4. Spec Synchronization

- [x] 4.1 Archive `display-collection-summary` with OpenSpec after all implementation tasks pass, synchronize `barcode-collection-session`, and strictly validate the archived/canonical state.
