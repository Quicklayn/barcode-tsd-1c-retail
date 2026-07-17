## Why

The current application can identify one product but cannot retain scan results as useful work. The next smallest product step is a durable barcode collection session that an operator can finish and send to 1C without changing stock or posting business documents.

## What Changes

- Add one local Android collection session that survives application restarts.
- Add found products as lines while preserving the opaque 1C item reference; a repeated scan increments the existing quantity.
- Allow the operator to review lines, change a positive quantity, delete a line, complete a non-empty session, and retry sending it after an error.
- Add one idempotent API operation that accepts the complete session and returns the same 1C result for repeated submissions with the same session identifier.
- Store each accepted session in an extension-owned, unposted 1C document associated with one warehouse configured in the extension.
- Keep online product lookup and keyboard-wedge/manual scanner input as the only item capture path.

Non-goals are background synchronization, an offline product catalog, vendor scanner SDKs, marking/GS1 processing, characteristics and series, multiple warehouses, assignment of 1C documents to devices, and automatic posting or stock movements.

## Capabilities

### New Capabilities

- `barcode-collection-session`: Local collection lifecycle, line aggregation and editing, completion, idempotent submission, and unposted 1C receipt.

### Modified Capabilities

None.

## Impact

- `android/**`: local persistence, collection screen behavior, retained `itemRef`, submission client, and JVM tests; `minSdk=26` remains unchanged.
- `docs/api/tsd-api.yaml`: one collection-session submission operation and its schemas.
- `extension/**`: extension-owned storage metadata, warehouse setting, HTTP handler, access rights, and validation.
- `openspec/**`: one new capability specification and synchronized project specification after archive.
- Runtime dependency impact is limited to Android Room persistence and its build-time code generation; WorkManager, Compose, dependency injection, and a multi-module split are not added.

Rollback removes the new operation, endpoint, and extension-owned storage objects while leaving the existing barcode lookup capability intact. `RT3/**` remains read-only.
