## Why

The collection screen shows individual lines but does not show the operator the current number of distinct positions or their total quantity. A compact derived summary makes scan and quantity changes immediately auditable without introducing planned quantities or a new operation model.

## What Changes

- Display `Позиций: N · Количество: X` above the visible line list.
- Recompute the summary from the current session after restore, scan aggregation, ambiguous-candidate selection, quantity edits, deletion, completion, and send-state rendering.
- Format zero, whole, and fractional totals without unnecessary trailing decimal zeros.
- Keep planned quantity, completion percentage, product units, per-operation targets, and history outside this change.
- Keep the summary derived in memory; do not add Room columns, migrations, API fields, dependencies, or 1C behavior.

Rollback removes the summary view and its formatting helper without changing stored sessions or backend data.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `barcode-collection-session`: require a visible, automatically refreshed summary of distinct positions and summed quantities for every local session state.

## Impact

- Android UI/resources and focused JVM/instrumentation tests under `android/**`.
- No changes to `extension/**`, `docs/api/**`, Room schema/version, Gradle dependencies, credentials, or `RT3/**`.

## Context sources

- Local `CollectionSession.kt` and `MainActivity.kt` confirm that line quantities are already durable three-decimal values and every mutation re-renders the current session.
- Canonical `barcode-collection-session` defines the existing restore and line-management behavior; 1C MCP checks were skipped because this Android-only change makes no 1C fact or behavior claim.
