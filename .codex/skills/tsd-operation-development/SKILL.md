---
name: tsd-operation-development
description: Build or extend a warehouse handheld operation as a small OpenSpec-governed vertical slice across Android, the OpenAPI contract, and the 1C extension. Use for collection, inventory, receiving, picking, shipment, or similar TSD workflows that create local documents and send results to 1C.
---

# TSD Operation Development

Implement one usable operation at a time. Keep `RT3/**` read-only, Android work under `android/**`, the contract under `docs/api/**`, and 1C work under `extension/**`.

## Workflow

1. Select or create one active OpenSpec change and lock its scenarios before coding.
2. Define the smallest document lifecycle needed by the operation: `draft`, `completed`, and `sent` unless the specification requires fewer states.
3. Lock the API payload before parallel implementation. Treat 1C references as opaque strings on Android.
4. Assign disjoint implementation scopes to the Kotlin and 1C developers. Keep OpenSpec and the API contract with the orchestrator.
5. Implement the Android operation end to end: resume the local draft, capture scans, show lines, edit/delete, complete, and send.
6. Implement an idempotent 1C receiver. Store the client session identifier and return the same result for retries.
7. Run contract, Android, 1C metadata/BSL, OpenSpec, and repository gates. Request read-only cross-stack review after integration.

## Guardrails

- Preserve the resolved `itemRef`; do not look up the same line again during submission.
- Increment quantity for a repeated scan of the same resolved item unless the active specification says otherwise.
- Reject empty documents and non-positive quantities before submission.
- Do not post business documents automatically. Receiving data and applying business effects are separate decisions.
- Do not add operations, configurable routing, generic workflow engines, or vendor scanner SDKs speculatively.
- Prefer a local database only when the operation must survive process or device restarts.

## Completion Evidence

Require executable tests for line aggregation and API serialization, strict OpenSpec validation, a successful Android build, and applicable 1C syntax/XML validation. Record infrastructure gaps explicitly; never mark a task complete from inspection alone.
