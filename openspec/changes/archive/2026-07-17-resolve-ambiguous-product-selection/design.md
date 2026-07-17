## Context

The 1C endpoint and OpenAPI contract already return an array of complete `ProductMatch` objects for `status=ambiguous`. The Android client reduces those objects to names, and the activity can only display them. The collection repository already has a safe expected-`sessionId` update path, but its current online method also writes the unambiguous lookup cache.

This change is Android-only, keeps `minSdk=26`, introduces no dependency or Room schema update, and preserves all existing online, cached, and submission behavior.

## Goals / Non-Goals

**Goals:**

- Preserve candidate identity from the existing response.
- Let the operator choose exactly one candidate in a compact modal list.
- Aggregate the choice into the expected active draft without caching the ambiguous mapping.
- Keep cancel/dismiss and lifecycle-stale selection non-mutating.

**Non-Goals:**

- Automatic candidate ranking or remembering the operator's choice.
- Changing 1C lookup rules, response fields, or the OpenAPI contract.
- Adding article, SKU, characteristic, package, stock, or price details.
- Adding a new screen, navigation framework, dependency, cache policy, or Room migration.

## Decisions

### 1. Preserve typed candidates in `LookupResult.Ambiguous`

Replace the names-only list with immutable candidates containing `itemRef` and name. The parser accepts an ambiguous result only when at least two candidates are complete; malformed candidate data maps to the existing server/protocol error surface.

Alternative considered: keep names and recover references later. Rejected because the selection must preserve the exact server-provided identity and no second endpoint exists.

### 2. Use the existing `AlertDialog` pattern for one modal choice

Show candidates as numbered list rows in response order with a cancel action. No candidate is preselected, and dismissing the dialog restores scanner focus without changing the draft.

Alternative considered: add an inline candidate panel to the single activity. Rejected because it would enlarge the persistent screen for a transient exceptional state.

### 3. Add a repository path that aggregates without cache mutation

Add the selected candidate through `CollectionSessionDao.updateSession(expectedSessionId, ...)`, reusing the same stale-session and draft-state checks as other local mutations. This path constructs the collection line but never calls `upsertCachedProduct`.

Alternative considered: reuse `addResolvedProduct`. Rejected because that method intentionally caches only unambiguous online mappings and would make a non-unique barcode resolve silently while offline.

### 4. Keep the original lookup session identifier through selection

The activity passes the `sessionId` captured before the network request into the selection callback. If the active session changed or became immutable before the click is persisted, the repository rejects the mutation and the UI displays the existing validation error.

### 5. Leave existing cache rows unchanged

Selecting an ambiguous candidate neither creates nor replaces a cache row. Any row created by an earlier unambiguous successful lookup remains governed by the existing cache-retention specification; changing invalidation policy is outside this slice.

## Risks / Trade-offs

- [Candidates can have identical names] -> Number rows and preserve response order; richer product identifiers require a future API change.
- [Activity recreation dismisses the modal choice] -> No draft mutation occurs before selection, so the operator can rescan safely.
- [A stale click could target a replaced session] -> Persist through the expected-session transaction and reject mismatches.
- [A selected mapping could incorrectly become an offline truth] -> Use a dedicated non-caching repository method and test cache miss after selection.

## Migration Plan

1. Ship the updated Android APK; no database or backend migration is required.
2. Existing drafts, cache rows, settings, and sent sessions remain compatible.
3. Rollback by reinstalling the previous APK; Room remains at schema version 2.

## Open Questions

None.

## Context sources

- Local Android code: `BarcodeLookupClient.kt`, `MainActivity.kt`, and `CollectionDatabase.kt` define the current parsing, result presentation, cache-writing, and expected-session boundaries.
- Canonical specs: `tsd-product-lookup` and `barcode-collection-session` define ambiguous lookup behavior and aggregation invariants.
- `docs/api/tsd-api.yaml` already provides every field required by this design.
- 1C MCP checks skipped: no 1C source, metadata, platform API, or behavior change is proposed.
