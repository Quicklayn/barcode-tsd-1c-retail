# ADR 0001: SDD and MVP Architecture

## Status

Accepted.

## Context

The project starts from a local XML export of `1C:Retail 3.0` in `RT3/` and
needs a native Android application for TSD devices. The first business goal is
small: scan a product barcode on an Android TSD and show the product name from
1C.

## Decision

- Use OpenSpec as the only SDD workflow.
- Do not add Spec Kit or another parallel SDD framework.
- Keep `RT3/` as the read-only baseline export for analysis.
- Put new 1C integration work under `extension/` and prefer an extension-first
  backend.
- Put Android work under `android/` and use native Kotlin/Gradle.
- Store API contracts under `docs/api/`.
- Use online-only barcode lookup for the first MVP.

## Consequences

- Requirements and implementation tasks are reviewable before code is written.
- The first vertical slice stays small and reversible.
- The project can later add offline cache, inventory documents, stock balances,
  prices, and vendor-specific scanner integrations without changing the SDD
  workflow.

## Context Sources

- `RT3/Configuration.xml`: Retail 3.0 version and platform compatibility.
- `RT3/InformationRegisters/ШтрихкодыНоменклатуры.xml` and
  `RT3/Catalogs/ШтрихкодыУпаковокТоваров.xml`: standard barcode data sources.
- OpenSpec and Spec Kit public documentation reviewed before the decision.

