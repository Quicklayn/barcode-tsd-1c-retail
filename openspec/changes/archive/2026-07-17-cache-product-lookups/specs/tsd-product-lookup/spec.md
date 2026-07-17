## ADDED Requirements

### Requirement: Successful Product Lookup Cache
The Android application MUST persist the normalized barcode, 1C `itemRef`, and product name after every unambiguous successful online lookup and MUST replace the cached row when a later successful lookup resolves the same barcode.

#### Scenario: Online product is cached
- **WHEN** 1C returns one `found` product for a normalized barcode
- **THEN** the application persists that barcode, `itemRef`, and product name before presenting the successful result

#### Scenario: Online product mapping changes
- **WHEN** a later successful online lookup returns different product data for an already cached barcode
- **THEN** the application replaces the cached product data with the latest successful response

### Requirement: Cached Connection-Failure Fallback
The Android application MUST attempt local product lookup only after a network connection failure and MUST keep all authoritative 1C responses visible without cache substitution.

#### Scenario: Connection fails and barcode is cached
- **WHEN** the online request ends with a connection failure and the normalized barcode exists in the local cache
- **THEN** the application returns the cached `itemRef` and product name as a successful result marked as cached

#### Scenario: Connection fails and barcode is not cached
- **WHEN** the online request ends with a connection failure and the normalized barcode is absent from the local cache
- **THEN** the application displays the original connection error and does not report a product

#### Scenario: Authoritative response is not successful
- **WHEN** 1C returns `not_found`, `ambiguous`, an authentication error, or a server/protocol error
- **THEN** the application displays that result and does not replace it with cached data

### Requirement: Durable Compatible Cache Storage
The Android application MUST retain cached products across process restarts and MUST migrate an existing version-1 Room database to the cache-enabled schema without losing the active collection session or its lines.

#### Scenario: Application restarts after caching a product
- **WHEN** a cached product exists and the application process restarts
- **THEN** the same barcode can be resolved from the local cache after a connection failure

#### Scenario: Existing collection database is upgraded
- **WHEN** the application first opens an existing version-1 database after the update
- **THEN** Room migrates it to version 2 and preserves the session header, state, lines, quantities, and document reference

#### Scenario: Cached product has not been refreshed
- **WHEN** no later successful online lookup occurs for a cached barcode
- **THEN** the application retains the cached row until application data is cleared

## REMOVED Requirements

### Requirement: Online-Only MVP
**Reason**: The next vertical slice adds a deliberately narrow local fallback for products already resolved by the same device.

**Migration**: Existing users receive an empty cache after the Room version-1 to version-2 migration; normal online lookups populate it incrementally.

## Context sources

- Canonical `tsd-product-lookup` spec confirms the online-only behavior being replaced and the authoritative result states that remain unchanged.
- Local Android code confirms barcode normalization, `LookupResult` variants, and Room database version 1.
- 1C MCP checks skipped: the HTTP contract and 1C implementation are unchanged.
