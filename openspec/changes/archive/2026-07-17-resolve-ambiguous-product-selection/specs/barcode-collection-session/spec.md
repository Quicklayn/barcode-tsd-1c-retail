## MODIFIED Requirements

### Requirement: Resolved Scan Aggregation
The application MUST add only unambiguous online results, operator-selected candidates from online ambiguous responses, or unambiguous cached results produced after a connection failure to the active draft and MUST retain the resolved 1C `itemRef` for submission.

#### Scenario: New online product is scanned
- **WHEN** an online barcode lookup returns an `itemRef` not present in the draft
- **THEN** the application adds a line with the returned `itemRef`, product name, barcode, and quantity `1`

#### Scenario: New ambiguous candidate is selected
- **WHEN** the operator selects an online ambiguous candidate whose `itemRef` is not present in the draft
- **THEN** the application adds a line with that candidate's `itemRef`, product name, response barcode, and quantity `1`
- **AND** the ambiguous barcode-to-product choice is not written to the product cache

#### Scenario: New cached product is scanned during a connection failure
- **WHEN** the online request fails to connect and the cache returns an `itemRef` not present in the draft
- **THEN** the application adds the cached line with quantity `1` and identifies the visible result as cached

#### Scenario: Product is scanned repeatedly
- **WHEN** an online result, selected ambiguous candidate, or cached result returns an `itemRef` already present in the draft
- **THEN** the application increments that line quantity by `1` without adding another line

#### Scenario: Lookup does not resolve one product
- **WHEN** lookup returns `not_found`, an unselected or cancelled `ambiguous` result, an authentication or server/protocol error, or a connection error without a cached product
- **THEN** the application displays that lookup state and does not change the draft

## Context sources

- Canonical `barcode-collection-session` defines aggregation by `itemRef`, immutable completed/sent states, and expected-session transactional updates.
- `CollectionRepository` currently separates cache-writing online aggregation from cache-reading fallback aggregation.
- 1C MCP checks skipped: the submitted line shape, backend endpoint, and 1C validation remain unchanged.
