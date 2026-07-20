## Why

After a barcode is resolved, a TSD operator can see the product name but cannot
check whether that product is currently available in the configured warehouse.
An explicit stock lookup is the smallest read-only warehouse capability that
builds on the existing scan result without changing collection documents.

## What Changes

- Add a read-only `POST /v1/product-stock/resolve` operation that accepts one
  resolved `itemRef` and returns its current quantity from the configured
  warehouse.
- Read `РегистрНакопления.ЗапасыНаСкладах` by `Номенклатура` and
  `СтруктурнаяЕдиница` from `Константа.BarcodeTSD_Склад`.
- Add the minimal TSD role permission required to read the stock register.
- Add an explicit Android action for the latest resolved product; it displays a
  successful online quantity or the server error and does not change the draft.

## Capabilities

### New Capabilities
- `tsd-product-stock`: Read-only current-stock lookup for a resolved product in
  the configured TSD warehouse.

### Modified Capabilities
- `tsd-product-lookup`: A successful lookup exposes the explicit stock action
  without automatically querying or displaying stock.

## Impact

- `extension/src/HTTPServices/BarcodeTSD/**` and
  `extension/src/Roles/BarcodeTSD_Use/Ext/Rights.xml`.
- `docs/api/tsd-api.yaml`.
- Android client, activity, resources, and tests under `android/**`.
- No new dependencies, Room schema changes, 1C documents, posting, or changes
  under `RT3/**`.

## Context sources

Verified from the local read-only export: `РегистрНакопления.ЗапасыНаСкладах`
has resource `Количество` and dimensions `Номенклатура` and
`СтруктурнаяЕдиница`; `Константа.BarcodeTSD_Склад` is
`СправочникСсылка.СтруктурныеЕдиницы`. MCP metadata tools are not exposed in
this session.
