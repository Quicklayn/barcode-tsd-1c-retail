## 1. OpenSpec Planning

- [x] 1.1 Validate the complete `add-product-stock-lookup` proposal and delta specs with `npm exec -- openspec validate add-product-stock-lookup --strict` before implementation starts.

## 2. Contract And 1C Extension

- [x] 2.1 Update `docs/api/tsd-api.yaml` with the exact `POST /v1/product-stock/resolve` request, accepted response, and `400`/`409` errors; verify contract consistency.
- [x] 2.2 Update the existing `BarcodeTSD` HTTP service route and module plus `BarcodeTSD_Use` role to validate one product reference, query aggregate warehouse stock, and remain read-only; verify extension structure and applicable BSL/XML checks.

## 3. Android Stock Action

- [x] 3.1 Add a focused Android stock client and unit tests that serialize the exact request, normalize the endpoint, apply Basic authentication, and map contract responses.
- [x] 3.2 Update `MainActivity`, resources, and instrumentation tests so the explicit stock action is available only for the latest resolved product, displays compact quantities, and preserves every collection state after failure or success.

## 4. Verification And Review

- [x] 4.1 Run Android JVM tests, lint, debug assembly, instrumentation-test assembly, and the affected connected tests with `minSdk=26` and Room schema version unchanged.
- [x] 4.2 Request a read-only `tsd-reviewer` review of the integrated diff, resolve every blocking finding, and verify no changes under `RT3/**`, no 1C business writes, and no new dependencies or Room migration.
- [x] 4.3 Run `pwsh -NoProfile -File .\scripts\quality\Invoke-QualityGate.ps1 -Mode Full -DiffMode Working`; strict OpenSpec, extension, contract, and Android checks must pass.

## 5. Spec Synchronization

- [x] 5.1 Archive `add-product-stock-lookup` after all checks pass, synchronize both affected canonical specs, and strictly validate the archived state.
