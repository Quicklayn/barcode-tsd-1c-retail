## MODIFIED Requirements

### Requirement: Resolved Scan Aggregation
The application MUST add only unambiguous online results or unambiguous cached results produced after a connection failure to the active draft and MUST retain the resolved 1C `itemRef` for submission.

#### Scenario: New online product is scanned
- **WHEN** an online barcode lookup returns an `itemRef` not present in the draft
- **THEN** the application adds a line with the returned `itemRef`, product name, barcode, and quantity `1`

#### Scenario: New cached product is scanned during a connection failure
- **WHEN** the online request fails to connect and the cache returns an `itemRef` not present in the draft
- **THEN** the application adds the cached line with quantity `1` and identifies the visible result as cached

#### Scenario: Product is scanned repeatedly
- **WHEN** an online or cached result returns an `itemRef` already present in the draft
- **THEN** the application increments that line quantity by `1` without adding another line

#### Scenario: Lookup does not resolve one product
- **WHEN** lookup returns `not_found`, `ambiguous`, an authentication or server/protocol error, or a connection error without a cached product
- **THEN** the application displays that lookup state and does not change the draft

## Context sources

- Canonical `barcode-collection-session` spec defines aggregation by `itemRef` and the immutable completed/sent states retained by this change.
- Local Android code confirms session mutations are serialized through Room by expected `sessionId`.
- 1C MCP checks skipped: submitted line shape and server-side validation are unchanged.
