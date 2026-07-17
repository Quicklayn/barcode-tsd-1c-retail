# 1C Extension Workspace

This folder is reserved for the 1C extension that will expose the TSD backend
API without modifying the `RT3/` baseline export directly.

## Baseline Decisions

- Extension internal name: `BarcodeTSD`.
- New 1C development is extension-first.
- `RT3/` remains the read-only source of facts about the standard Retail 3.0
  configuration.
- HTTP service metadata object name: `BarcodeTSD`.
- HTTP endpoints:
  - `POST /hs/BarcodeTSD/v1/barcode/resolve`
  - `POST /hs/BarcodeTSD/v1/barcode-collection-sessions`
- The first backend capability is a read-only HTTP endpoint for product barcode
  lookup.
- The endpoint must use standard Retail barcode metadata for the MVP:
  - `РегистрСведений.ШтрихкодыНоменклатуры`
- The first response returns only the product name and an opaque item reference.

## Barcode Collection Sessions

- `Константа.BarcodeTSD_Склад` stores the warehouse used for new sessions. It
  must reference an existing `Справочник.СтруктурныеЕдиницы` item whose type is
  `Склад`.
- `Документ.BarcodeTSD_СборШтрихкодов` stores the accepted lines. Its unique
  non-periodic string number is the session UUID; the document is written
  unposted and produces no register movements.
- `РегистрСведений.BarcodeTSD_ПринятыеСессии` stores the session UUID and its
  original document reference. The handler locks this key and writes the
  document and register record atomically.
- An equivalent retry returns the original document reference. Reusing a
  session UUID with different lines returns `409 idempotency_conflict`.

Before using the collection endpoint, set `BarcodeTSD_Склад` and assign the
minimal `BarcodeTSD_Use` role to the technical HTTP-service user.

## Web Publication

- The target platform accepts the HTTP service from the `BarcodeTSD` extension.
- The 1C web publication must allow HTTP services from extensions. In
  `default.vrd`, the `<httpServices>` node must include
  `publishExtensionsByDefault="true"`.
- If the publication still returns `404`, add an explicit service entry for the
  extension service:

```xml
<httpServices publishByDefault="true" publishExtensionsByDefault="true">
  <service name="BarcodeTSD" rootUrl="BarcodeTSD" enable="true"
           reuseSessions="dontuse" sessionMaxAge="20" />
</httpServices>
```
