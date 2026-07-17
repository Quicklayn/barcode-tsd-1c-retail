## Context

The existing native Android application performs synchronous online barcode lookup and displays a product name. The 1C extension exposes only `/v1/barcode/resolve`; it writes no data. This change introduces the first durable TSD operation while preserving Android 8 compatibility, the existing lookup behavior, extension-first placement, and the read-only `RT3/**` baseline.

The operation is deliberately generic barcode collection, not inventory or receiving. It captures what was scanned and sends the completed result to 1C without posting or changing stock.

## Goals / Non-Goals

**Goals:**

- Preserve one local collection across process restarts.
- Aggregate repeated resolved products and allow draft line maintenance.
- Freeze a completed collection and retain it through failed manual sends.
- Accept the complete session idempotently into one unposted 1C document.
- Keep one warehouse as a 1C-side extension setting.
- Retain a small single-module Android application and the current Basic authentication boundary.

**Non-Goals:**

- Automatic/background retry, offline product lookup, conflict merging, or server-to-device assignments.
- Inventory, receiving, picking, shipping, posting, or stock movements.
- Multiple warehouses, characteristics, series, packages, marking, or GS1 parsing.
- Compose, dependency injection, navigation frameworks, multi-module structure, or vendor scanner SDKs.

## Decisions

### 1. One active local document stored with Room

Android will use Room for a session header and its lines because both must be restored together and line edits must be transactional. The persisted state is `draft`, `completed`, or `sent`; a transient network request does not introduce a durable `sending` state. Only one non-sent session may exist, and the user explicitly starts a fresh draft after a successful send.

Alternative considered: JSON in `SharedPreferences`. It avoids a dependency but makes atomic header/line edits and later operation growth fragile. WorkManager is not added because this change has manual retry only.

### 2. Lookup remains online and feeds the operation

`LookupResult.Found` will retain `itemRef` as well as barcode and name. The existing keyboard-wedge/manual submission path calls lookup, then a collection service aggregates by `itemRef`. Non-found, ambiguous, and error results do not mutate the draft.

Alternative considered: send barcodes and resolve all products again in 1C during completion. That permits drift between what the operator saw and what is stored, so the accepted lookup reference is preserved instead.

### 3. Send the complete immutable session

The new `POST /v1/barcode-collection-sessions` operation sends `sessionId` and all lines. Each line contains `itemRef`, the source `barcode`, and decimal `quantity`. Product names remain local display data and are not trusted as 1C input. The service returns `status=accepted`, `sessionId`, and `documentRef` for both first submission and retries.

Alternative considered: line-by-line events. A complete payload is easier to validate, retry, and make idempotent and avoids partial server documents.

### 4. Store the result in an extension-owned unposted document and idempotency register

The extension will add `Документ.BarcodeTSD_СборШтрихкодов` with string number length 36, non-periodic unique numbering, a `Склад` attribute of type `СправочникСсылка.СтруктурныеЕдиницы`, and `Товары` lines containing `Номенклатура`, `Штрихкод`, and `Количество`. The client UUID is written as the document number. The document is written without posting and has no stock register movements.

An independent non-periodic `РегистрСведений.BarcodeTSD_ПринятыеСессии` stores dimension `ИдентификаторСессии` (string 36) and resource `Документ` (`ДокументСсылка.BarcodeTSD_СборШтрихкодов`). Inside one transaction, the handler takes a managed data lock on the session dimension, checks the register, writes a new document and register record atomically, then commits. An existing key is accepted only when the registered document lines equal the validated request by unique `itemRef`, barcode, and exact three-decimal quantity regardless of line order; a different payload returns `409 idempotency_conflict`. Unique document numbering remains a secondary database guard and a visible correlation value.

Alternative considered: write directly to a standard Retail business document. Generic barcode collection does not yet define a business operation, so selecting and populating a standard document would add unapproved semantics and upgrade coupling.

### 5. Configure one warehouse in an extension constant

`Константа.BarcodeTSD_Склад` stores one `СправочникСсылка.СтруктурныеЕдиницы`. The HTTP technical role receives only the rights required to read this setting and create/read the collection document. A new session fails with `409 warehouse_not_configured` when the setting is empty. A retry of an already accepted session does not depend on the current setting.

Alternative considered: expose warehouse selection and discovery to Android. That adds another API and UI workflow before multiple warehouses are required.

### 6. Validate before any 1C write

The handler accepts only the OpenAPI shape, limits the collection to 1000 unique item references, validates UUIDs, barcode length, and positive decimal quantities, resolves all product references, and checks the configured value is a warehouse structural unit before creating a document. Unexpected failures use the existing generic server error response and do not expose internal exception text.

### 7. Keep ownership disjoint

- Parent/orchestrator: `openspec/**`, `docs/api/**`, `.codex/skills/**`, integration, task state, and final gates.
- `tsd-kotlin-developer`: `android/**` only.
- `tsd-1c-developer`: `extension/**` only, including metadata generation and validation.
- `tsd-reviewer`: read-only integrated review after both implementations and tests exist.

## Risks / Trade-offs

- [Room code generation must remain compatible with AGP 9 and built-in Kotlin] -> Pin one compatible Room/KSP toolchain in Gradle and verify from a clean build.
- [A technical user could write collection documents outside the HTTP handler if granted broad rights] -> Keep the role limited to this extension document, the warehouse constant, product read, and the existing HTTP service.
- [A configured structural unit might not be a warehouse] -> Validate `ТипСтруктурнойЕдиницы` against `Перечисление.ТипыСтруктурныхЕдиниц.Склад` before accepting a new session.
- [Two identical submissions can arrive concurrently] -> Serialize them with a managed lock on `BarcodeTSD_ПринятыеСессии.ИдентификаторСессии` and write the register/document in one transaction; retain the unique document number as a second guard.
- [A client could reuse a session UUID with different lines] -> Compare the validated request with the registered unposted document and return `409 idempotency_conflict` on any difference.
- [A completed local session cannot be corrected] -> Keep the state immutable by design; the operator starts a new session after a mistake. Reopening and correction audit are later capabilities.

## Migration Plan

1. Update the OpenAPI contract and implement both clients against it.
2. Load the extension document, constant, and idempotency-register metadata additions before installing the updated APK.
3. Configure `BarcodeTSD_Склад` with a structural unit whose type is warehouse and assign the updated `BarcodeTSD_Use` role to the HTTP user.
4. Install the APK; Room creates the local schema on first launch. Existing connection preferences and barcode lookup remain available.
5. Verify lookup, draft recovery, completion, first send, and duplicate send against a test infobase.

Rollback installs the previous APK and removes the new HTTP method and setting. Keep or export accepted collection documents before removing their metadata object; the existing lookup endpoint remains unchanged.

## Verification

- Strict OpenSpec validation and contract consistency script.
- Android JVM tests for aggregation, persistence-facing state rules, JSON mapping, and idempotent response handling; then `test`, `lint`, and debug `assemble`.
- 1C XML validation for the constant, document, HTTP service, role, and configuration; BSL syntax, quality, and review validators when exposed.
- Full repository quality gate followed by read-only cross-stack review.

## Open Questions

None for this change.

## Context Sources

- `RT3/Catalogs/СтруктурныеЕдиницы.xml`: warehouse reference type and `ТипСтруктурнойЕдиницы=Склад` evidence.
- `RT3/InformationRegisters/ШтрихкодыНоменклатуры.xml`: product reference used by the current lookup.
- `extension/src/Configuration.xml`, `extension/src/HTTPServices/BarcodeTSD.xml`, and `extension/src/Roles/BarcodeTSD_Use/Ext/Rights.xml`: current extension compatibility, endpoint, and least-privilege baseline.
- `android/build.gradle.kts`, `android/app/build.gradle.kts`, and current Kotlin sources: AGP, built-in Kotlin, `minSdk=26`, and single-module baseline.
- 1C MCP metadata, memory, platform, and ITS tools were not exposed in this session; no unavailable result is assumed.
