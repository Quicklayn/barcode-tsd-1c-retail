## 1. Contract and Workflow

- [x] 1.1 Parent/orchestrator updates `docs/api/tsd-api.yaml` with the complete idempotent session operation; check: OpenAPI parsing and `docs/testing/test-contract-consistency.ps1` pass.
- [x] 1.2 Parent/orchestrator installs and validates `.codex/skills/android-cli`, `tsd-operation-development`, `tsd-scanner-integration`, and `tsd-offline-sync`; check: every skill has valid frontmatter and no TODO placeholders.
- [x] 1.3 Parent/orchestrator validates all planning artifacts; check: `npm exec -- openspec validate barcode-collection-session --strict` passes before stack implementation is accepted.

## 2. Android Implementation (`tsd-kotlin-developer`, `android/**`)

- [x] 2.1 Preserve `itemRef` in successful lookup results and add a collection domain model with tested aggregation, positive quantity edits, deletion, and lifecycle transitions; check: focused JVM tests pass.
- [x] 2.2 Add Room-backed session and line persistence with one restorable active document and a schema suitable for `minSdk=26`; check: the project compiles and persistence integration is covered by the applicable Android test surface.
- [x] 2.3 Implement the collection UI in the existing single activity: restored lines, keyboard-wedge/manual scans, quantity edit/delete, completion, manual retry, sent state, and new draft; check: observable controls follow the active delta and existing lookup failures do not mutate lines.
- [x] 2.4 Add the `/v1/barcode-collection-sessions` client and response/error mapping; check: JVM HTTP tests verify exact request shape, Basic authentication, accepted response, and `400`/`401`/`403`/`409`/`5xx` handling.
- [x] 2.5 Run Android verification from `android/**`; check: Gradle JVM tests, lint, and debug assemble pass with `minSdk=26` unchanged.

## 3. 1C Implementation (`tsd-1c-developer`, `extension/**`)

- [x] 3.1 Generate extension-owned `Константа.BarcodeTSD_Склад`, `Документ.BarcodeTSD_СборШтрихкодов`, and independent `РегистрСведений.BarcodeTSD_ПринятыеСессии` metadata and update configuration/role rights; check: metadata XML and cross-references validate and `RT3/**` remains unchanged.
- [x] 3.2 Add the HTTP service template and BSL handler for strict validation, warehouse checks, product resolution, managed-lock/register idempotency, payload-conflict detection, unique UUID document numbering, and unposted atomic writes; check: applicable BSL syntax, quality, review, and metadata validators pass or an unavailable tool is explicitly recorded.
- [x] 3.3 Synchronize `extension/metadata/BarcodeTSD.httpservice.json` and extension documentation with the implemented endpoint and setup requirement; check: descriptors match `extension/src/HTTPServices/BarcodeTSD.xml`.

## 4. Integration and Completion

- [x] 4.1 Parent/orchestrator integrates both stacks and runs contract consistency plus strict OpenSpec validation; check: Android request/response fields and 1C handler fields exactly match `docs/api/tsd-api.yaml`.
- [x] 4.2 `tsd-reviewer` performs read-only review of the integrated diff against the delta; check: all blocking findings are fixed or explicitly rejected with evidence.
- [x] 4.3 Parent/orchestrator runs the full repository quality gate and confirms no changes under `RT3/**`; check: `scripts/quality/Invoke-QualityGate.ps1 -Mode Full -DiffMode Working` succeeds.
- [x] 4.4 Parent/orchestrator marks only evidenced tasks complete, synchronizes the new capability, and archives the change with the local OpenSpec CLI; check: no active change remains and `openspec/specs/barcode-collection-session/spec.md` contains the accepted requirements.
