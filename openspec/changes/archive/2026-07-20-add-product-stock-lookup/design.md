## Context

The Android TSD already resolves a barcode to an opaque 1C `itemRef`, keeps a
durable collection draft, and sends completed sessions to 1C. The extension
already owns one configured warehouse constant, but the operator cannot read
the current balance for a resolved product. The new capability is read-only and
must remain compatible with Android 8.0 (`minSdk=26`).

## Goals / Non-Goals

**Goals:**

- Return the current quantity of one resolved `Справочник.Номенклатура` item
  in the configured `Справочник.СтруктурныеЕдиницы` warehouse.
- Make the lookup deliberate in the Android UI and keep the collection draft
  unchanged for every stock outcome.
- Use the existing Basic-auth publication boundary and extension-first layout.

**Non-Goals:**

- Stock cache, background refresh, prices, reservations, characteristics,
  batches, cells, per-organization breakdowns, or multiple warehouse choice.
- New documents, posting, register movements, Room schema migrations, or
  changes to `RT3/**`.

## Decisions

### Separate item-reference endpoint

Use `POST /v1/product-stock/resolve` with exactly `{ "itemRef": "<uuid>" }`.
The resolved reference is already retained by Android and avoids repeating
barcode matching or choosing an ambiguous candidate. `POST` preserves the
existing strict JSON validation and Basic-auth request pattern. Extending the
barcode response was rejected because it would either add a stock query to
every scan or leave selected ambiguous candidates without a consistent path.

### One configured warehouse and aggregate quantity

Read `Константа.BarcodeTSD_Склад`; when it is empty or no longer a valid
`Справочник.СтруктурныеЕдиницы` item, return `409 warehouse_not_configured`.
For a valid product and warehouse, sum `КоличествоОстаток` from
`РегистрНакопления.ЗапасыНаСкладах.Остатки` filtered by `Номенклатура` and
`СтруктурнаяЕдиница`. Other register dimensions are intentionally aggregated,
so the result is one current warehouse quantity rather than a characteristic,
batch, cell, or organization breakdown.

Return `{ "status": "found", "itemRef": "<uuid>", "quantity": <number> }`
with at most three fractional digits. Zero and negative balances are valid
read-only accounting results. Malformed, unknown, and group product references
return `400 invalid_request`.

### Explicit foreground Android action

Keep the latest resolved product only in `MainActivity` runtime state. Show
`Показать остаток` after an unambiguous online result, a selected ambiguous
candidate, or a cached resolved product. The button makes the new network call
on the existing background executor; while it runs, normal in-progress guards
apply. A completed, sent, or draft collection is never edited by this action.

The quantity is rendered as a plain decimal without trailing zeros. A stock
failure preserves the latest product presentation and draft, then displays the
server or connection diagnostic. No stock value is cached, so an offline
fallback cannot present stale availability as current.

### Ownership and verification

The parent owns OpenSpec and `docs/api/tsd-api.yaml`. The 1C scope is limited
to the existing HTTP service route/module and `BarcodeTSD_Use` role; the
Android scope is limited to a dedicated stock client, `MainActivity`, resources,
and focused tests. The final review is read-only.

## Risks / Trade-offs

- [A balance can change immediately after the response] → label it as the
  current online balance and do not reserve or post anything.
- [The shared warehouse constant can be unset] → report the existing
  configuration conflict explicitly and preserve the local draft.
- [The underlying register has more dimensions] → aggregate intentionally and
  keep detailed breakdowns out of this slice.
- [No live infobase is configured in `.dev.env`] → complete static, Android,
  contract, and extension validation; defer live HTTP smoke evidence rather
  than claim it passed.

## Migration Plan

Deploy the extension and Android APK together with the updated OpenAPI
contract. Assign the updated `BarcodeTSD_Use` role to the technical HTTP user
and configure `BarcodeTSD_Склад`. Rollback removes only the new route, role
read permission, Android action, and contract entry; no data migration or
business-document cleanup is required.

## Open Questions

None.

## Context sources

Verified from local metadata: `РегистрНакопления.ЗапасыНаСкладах` is a balance
register with `Количество` (15,3), `Номенклатура`, and
`СтруктурнаяЕдиница`; `BarcodeTSD_Склад` has type
`CatalogRef.СтруктурныеЕдиницы`. Existing endpoint, validation, and role
patterns were read from `extension/**`. MCP metadata tools are not exposed in
this session.
