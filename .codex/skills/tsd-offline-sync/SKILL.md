---
name: tsd-offline-sync
description: Design or implement durable local TSD documents and idempotent synchronization with 1C. Use when a handheld operation must survive app restarts, queue completed work, retry transmission, or reconcile local and server states.
---

# TSD Offline Sync

Treat local operation data as the working source until 1C acknowledges the completed document.

## Data Model

- Give every local document a client-generated UUID that remains stable across retries.
- Persist header state and lines transactionally.
- Store opaque 1C references, display names, source barcodes, quantities, and the last confirmed server result needed by the active operation.
- Keep one explicit state transition path. A typical path is `draft -> completed -> sending -> sent`; return `sending` to `completed` after a failed request.

## Synchronization

1. Send the complete immutable document, not incremental line events.
2. Use the document UUID as the idempotency key in both the payload and 1C storage.
3. Accept a repeated successful response as success and preserve the returned 1C reference.
4. Keep failed completed documents locally and expose a manual retry.
5. Add WorkManager only when automatic background retry is a stated requirement. Use network constraints and unique work per document when it is added.

## Guardrails

- Do not delete local data before a confirmed server response.
- Do not store 1C credentials inside operation rows or logs.
- Do not silently merge server-side business changes into a completed local document.
- Use Room when several related entities, transactional updates, or queryable queues are needed; do not add it for a single scalar preference.

## Verification

Test process-restart recovery, atomic quantity edits, retry after connection failure, duplicate submissions, state transitions, and retention of an acknowledged server reference.
