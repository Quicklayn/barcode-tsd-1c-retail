## ADDED Requirements

### Requirement: Ambiguous Product Candidate Selection
The Android application MUST retain every complete `itemRef` and product name from an online `ambiguous` response and MUST require an explicit operator choice before treating any candidate as resolved.

#### Scenario: Complete ambiguous response is received
- **WHEN** 1C returns `status=ambiguous` with at least two candidates containing non-empty `itemRef` and name values
- **THEN** the application displays one modal selectable row per candidate in response order
- **AND** the draft remains unchanged until the operator chooses a row

#### Scenario: Operator chooses a candidate
- **WHEN** the operator selects one candidate from the modal list
- **THEN** the application closes the list and retains that candidate's exact `itemRef`, name, and response barcode for collection aggregation

#### Scenario: Operator cancels candidate selection
- **WHEN** the operator cancels or dismisses the candidate list without choosing a row
- **THEN** the application does not resolve a product, does not change the draft, and restores scanner input focus

#### Scenario: Ambiguous payload is incomplete
- **WHEN** an `ambiguous` response contains fewer than two complete candidates or any candidate lacks `itemRef` or name
- **THEN** the application displays a server/protocol error and does not offer candidate selection

#### Scenario: Selected ambiguous mapping is not cached
- **WHEN** the operator selects a candidate from an `ambiguous` response
- **THEN** the application does not create or replace a cached product row for that barcode

## Context sources

- `docs/api/tsd-api.yaml` requires `itemRef` and `name` for every `ProductMatch`, including matches in an `ambiguous` response.
- `BarcodeLookupClient.kt` currently retains only candidate names, while `MainActivity.kt` displays the names without an action.
- 1C MCP checks skipped: the existing HTTP response contract and backend behavior are unchanged by this Android-only delta.
