# tsd-product-stock Specification

## Purpose
TBD - created by archiving change add-product-stock-lookup. Update Purpose after archive.
## Requirements
### Requirement: Configured Warehouse Stock API
The 1C HTTP service MUST accept `POST /v1/product-stock/resolve` with exactly
one UUID `itemRef`, read the configured Barcode TSD warehouse, and return the
current aggregate quantity for that product.

#### Scenario: Product stock is found
- **WHEN** a request contains an existing non-group `Справочник.Номенклатура`
  reference and `BarcodeTSD_Склад` contains an existing warehouse
- **THEN** the service returns HTTP `200` with `status=found`, the same
  `itemRef`, and a numeric `quantity`
- **AND** `quantity` equals the sum of `КоличествоОстаток` from
  `РегистрНакопления.ЗапасыНаСкладах.Остатки` for that nomenclature and
  warehouse across its other dimensions

#### Scenario: Zero or negative balance is returned
- **WHEN** the aggregate warehouse quantity is zero or negative
- **THEN** the service returns HTTP `200` and that exact value with at most
  three decimal places

#### Scenario: Product reference is invalid
- **WHEN** JSON is malformed, contains fields other than `itemRef`, contains an
  invalid UUID, or identifies a missing or group nomenclature item
- **THEN** the service returns HTTP `400` with `error=invalid_request`
- **AND** it does not create or modify application data

#### Scenario: Warehouse is not configured
- **WHEN** a valid product request is received while `BarcodeTSD_Склад` is
  empty or no longer identifies a warehouse
- **THEN** the service returns HTTP `409` with `error=warehouse_not_configured`
- **AND** it does not create or modify application data

### Requirement: Read-Only TSD Stock Access
The extension role `BarcodeTSD_Use` MUST grant the technical TSD user read
access to `РегистрНакопления.ЗапасыНаСкладах` and MUST not grant write access
through the stock lookup capability.

#### Scenario: Technical user reads a balance
- **WHEN** a user with `BarcodeTSD_Use` calls the stock endpoint
- **THEN** the endpoint can read the configured warehouse balance
- **AND** the endpoint writes no documents, registers, or constants

### Requirement: Explicit Android Stock Display
The Android application MUST expose a stock action only after it has a resolved
product `itemRef`, and MUST request the current quantity only after the
operator invokes that action.

#### Scenario: Operator requests current stock
- **WHEN** an unambiguous lookup, selected ambiguous candidate, or cached
  resolved product is visible and the operator invokes `Показать остаток`
- **THEN** the application sends that exact `itemRef` to
  `/v1/product-stock/resolve`
- **AND** displays `Остаток: X` with a plain decimal quantity without trailing
  zeros after an accepted response

#### Scenario: No product is resolved
- **WHEN** the latest lookup is not found, cancelled, incomplete, or failed
- **THEN** the stock action is unavailable
- **AND** the application does not call the stock endpoint

#### Scenario: Stock lookup fails
- **WHEN** the stock endpoint returns an error or the connection fails
- **THEN** the application preserves the latest product result and collection
  session
- **AND** displays the returned or connection diagnostic without presenting a
  cached stock value

#### Scenario: Scan does not automatically request stock
- **WHEN** the application resolves and aggregates a barcode into a draft
- **THEN** it does not automatically call `/v1/product-stock/resolve`
- **AND** the collection behavior remains unchanged until the operator invokes
  the stock action
