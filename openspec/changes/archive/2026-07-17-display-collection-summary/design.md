## Context

`MainActivity` already receives a complete immutable `CollectionSession` after every repository mutation and calls `renderSession`, while Room restores the same lines and three-decimal quantities after process restart. The screen renders each line but provides no aggregate view of the current collection.

## Goals / Non-Goals

**Goals:**

- Show the number of distinct collection lines and the exact sum of their quantities.
- Refresh the values through the existing session-rendering path for draft, completed, and sent sessions.
- Keep whole and fractional totals compact and deterministic on Android 8+.

**Non-Goals:**

- Planned quantities, completion percentages, product units, package conversion, or operation targets.
- New persistence, migrations, backend fields, 1C changes, dependencies, or navigation.
- A redesign of the existing single-activity screen.

## Decisions

### 1. Derive the summary during session rendering

Add one stable `TextView` above the collection line container and calculate its value from the `CollectionSession` passed to `renderSession`. Every existing mutation already replaces `currentSession` and calls this renderer, so no event-specific counters or additional state are needed.

Alternative considered: persist totals in Room. Rejected because totals are fully derivable and duplicated persisted values could drift from the lines.

### 2. Use neutral Russian labels without units or plural inflection

Render exactly `Позиций: N · Количество: X`. This avoids incorrect assumptions that every quantity is measured in pieces and avoids locale-dependent Russian plural forms on devices configured with another locale.

Alternative considered: `N строк, X шт.`. Rejected because quantities may be fractional and the product unit is not part of the current contract.

### 3. Sum decimal quantities exactly and remove only trailing zeros

Sum existing exact three-decimal line quantities and format the result as plain decimal text: `0`, `2`, and `3.25`, never scientific notation or locale-dependent grouping. No rounding is introduced.

### 4. Keep ownership Android-only

The Kotlin developer owns `android/**`; the parent owns this OpenSpec change and integration; the reviewer remains read-only. `extension/**`, `docs/api/**`, Room schema/version, Gradle dependencies, credentials, and `RT3/**` are explicit no-change boundaries.

## Risks / Trade-offs

- [Operators may interpret quantity as progress] -> Label it only as factual `Количество` and do not show percentages or targets.
- [A stale counter could survive a mutation] -> Derive it from the immutable session inside the common renderer and cover scan, edit, delete, and restore paths with tests.
- [Decimal formatting could add zeros or scientific notation] -> Use exact decimal conversion and plain-string formatting in one helper.

## Migration Plan

Ship the updated APK without data migration. Existing sessions render their summary immediately after load. Rollback installs the previous APK; stored rows and backend data remain unchanged.

## Open Questions

None.

## Context sources

- `MainActivity.kt` centralizes UI updates in `renderSession`; `CollectionSession.kt` limits a session to 1000 unique lines and stores every quantity exactly in milli-units.
- Canonical `barcode-collection-session` defines durable restore and all current mutation paths; 1C MCP checks were skipped because no 1C metadata, platform API, or backend behavior is involved.
