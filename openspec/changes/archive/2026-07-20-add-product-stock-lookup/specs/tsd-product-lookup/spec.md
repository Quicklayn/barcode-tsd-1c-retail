## MODIFIED Requirements

### Requirement: Minimal Result Surface

The Android application MUST display the product name for a successful lookup
and MAY expose an explicit stock action for the resolved product, but it MUST
not query or display stock until the operator invokes that action.

#### Scenario: Successful lookup exposes no automatic stock

- GIVEN the backend response includes an item reference for a found product
- WHEN the app displays the successful lookup result
- THEN the visible product result contains the product name
- AND the app exposes the stock action for that resolved item
- AND the app does not automatically call the stock endpoint or display stock,
  price, package, characteristic, or series data

#### Scenario: Lookup without one resolved product exposes no stock action

- GIVEN the backend response is `not_found`, incomplete, or unselected
  `ambiguous`
- WHEN the app displays the lookup state
- THEN it does not expose the stock action

## Context sources

Existing `Minimal Result Surface` behavior was read from
`openspec/specs/tsd-product-lookup/spec.md`; the explicit stock action is
specified by this change's `tsd-product-stock` capability.
