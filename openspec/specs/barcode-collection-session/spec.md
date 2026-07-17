# barcode-collection-session Specification

## Purpose
Define durable barcode collection sessions on Android TSD devices and their idempotent submission to 1C as unposted collection documents.
## Requirements
### Requirement: Durable Local Collection Draft
The Android application MUST maintain at most one active barcode collection draft and MUST restore it after application process restart.

#### Scenario: No draft exists
- **WHEN** the collection screen opens and no local draft exists
- **THEN** the application creates a draft with a client-generated UUID and no lines

#### Scenario: Existing draft is restored
- **WHEN** the application restarts with a local draft containing lines
- **THEN** the application displays the same session identifier, state, lines, and quantities

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

### Requirement: Collection Line Management
The application MUST show draft lines and MUST allow the operator to set a positive quantity or delete a line while the session is a draft.

#### Scenario: Quantity is changed
- **WHEN** the operator replaces a line quantity with a positive value of at most three decimal places
- **THEN** the application persists and displays the new quantity

#### Scenario: Quantity is invalid
- **WHEN** the operator enters zero, a negative value, or a non-numeric value
- **THEN** the application rejects the edit and preserves the previous quantity

#### Scenario: Line is deleted
- **WHEN** the operator confirms deletion of a draft line
- **THEN** the application removes the line from local storage and the visible list

### Requirement: Explicit Collection Lifecycle
The application MUST use the lifecycle `draft -> completed -> sent`, MUST make completed lines immutable, and MUST keep a completed session available for manual retry until 1C acknowledges it.

#### Scenario: Empty draft cannot be completed
- **WHEN** the operator attempts to complete a draft with no lines
- **THEN** the application keeps it in `draft` state and displays a validation error

#### Scenario: Non-empty draft is completed
- **WHEN** the operator completes a draft containing only positive quantities
- **THEN** the application persists `completed` state and disables scanning, quantity edits, and deletion

#### Scenario: Submission fails
- **WHEN** sending a completed session ends with a connection, authentication, validation, or server error
- **THEN** the application retains the complete local session and allows the operator to retry

#### Scenario: Submission succeeds
- **WHEN** 1C accepts a completed session
- **THEN** the application marks it `sent` and persists the returned 1C document reference

#### Scenario: New collection starts after success
- **WHEN** the operator starts a new collection from a sent session
- **THEN** the application creates a different draft UUID with no lines

### Requirement: Complete Session API
The 1C HTTP service MUST accept one complete collection session at the operation defined by `docs/api/tsd-api.yaml` and MUST reject payloads outside that contract.

#### Scenario: Valid completed session is submitted
- **WHEN** a request contains a UUID `sessionId` and between 1 and 1000 unique product lines with valid `itemRef`, barcode, and positive quantity
- **THEN** the service returns HTTP `200`, `status=accepted`, the same `sessionId`, and a 1C `documentRef`

#### Scenario: Duplicate product reference is submitted
- **WHEN** two request lines contain the same `itemRef`
- **THEN** the service returns HTTP `400` with `error=invalid_request` and creates no 1C document

#### Scenario: Unknown product reference is submitted
- **WHEN** a request line contains an `itemRef` that does not identify an existing `Справочник.Номенклатура` item
- **THEN** the service returns HTTP `400` with `error=invalid_request` and creates no 1C document

#### Scenario: Request shape is invalid
- **WHEN** JSON is malformed, contains extra top-level or line fields, has no lines, has more than 1000 lines, or contains an invalid UUID, barcode, or quantity
- **THEN** the service returns HTTP `400` with `error=invalid_request` and creates no 1C document

### Requirement: Idempotent Unposted 1C Receipt
The 1C extension MUST store each accepted `sessionId` in an extension-owned information register, MUST create its collection document once, and MUST resolve equivalent retries to that same unposted document.

#### Scenario: Session is accepted for the first time
- **WHEN** a valid session UUID has not been accepted before and the extension warehouse is configured
- **THEN** the service creates one unposted document with that UUID as its unique number, the configured `Справочник.СтруктурныеЕдиницы` warehouse, and one product row per request line

#### Scenario: Accepted session is retried
- **WHEN** the service receives the same valid `sessionId` again
- **AND** the validated lines equal the registered document lines
- **THEN** it returns HTTP `200`, `status=accepted`, and the original `documentRef` without creating or changing a document

#### Scenario: Session identifier is reused with different data
- **WHEN** the service receives a registered `sessionId` with lines that differ from the registered document
- **THEN** it returns HTTP `409` with `error=idempotency_conflict` without creating or changing a document

#### Scenario: Warehouse is not configured
- **WHEN** a new valid session is submitted while the extension warehouse constant is empty
- **THEN** the service returns HTTP `409` with `error=warehouse_not_configured` and creates no document

#### Scenario: Accepted document has no business effect
- **WHEN** a collection session is accepted
- **THEN** the created document remains unposted and the extension does not create stock register movements

### Requirement: Android 8 Scanner Compatibility
The collection workflow MUST remain compatible with Android 8.0 (`minSdk=26`) and MUST use the existing keyboard-wedge and manual-input lookup path.

#### Scenario: Scanner sends Enter
- **WHEN** a keyboard-wedge scanner enters a barcode and sends Enter while a draft is active
- **THEN** the application performs the same lookup and aggregation flow as manual submission

#### Scenario: Completed session receives scanner input
- **WHEN** scanner or manual input is submitted while the session is `completed` or `sent`
- **THEN** the application does not perform lookup and does not change any line

### Requirement: Visible Collection Summary
The Android application MUST display `Позиций: N · Количество: X` for the current collection session, where `N` is the number of distinct lines and `X` is the exact sum of all line quantities without trailing decimal zeros.

#### Scenario: Empty draft is displayed
- **WHEN** a new or restored draft contains no lines
- **THEN** the application displays `Позиций: 0 · Количество: 0`

#### Scenario: Distinct positions and fractional quantities are summarized
- **WHEN** a session contains two lines with quantities `2` and `1.250`
- **THEN** the application displays `Позиций: 2 · Количество: 3.25`

#### Scenario: Repeated product scan updates only total quantity
- **WHEN** a resolved scan increments the quantity of an existing line
- **THEN** the displayed position count remains unchanged
- **AND** the displayed total quantity increases by `1`

#### Scenario: Draft line is edited or deleted
- **WHEN** the operator successfully changes a line quantity or deletes a line
- **THEN** the application immediately recomputes both summary values from the persisted draft

#### Scenario: Session is restored or changes lifecycle state
- **WHEN** the application restores a session or renders it as `completed` or `sent`
- **THEN** the displayed summary equals the values derived from that session's stored lines

#### Scenario: Summary does not claim a unit or target
- **WHEN** the application displays the collection summary
- **THEN** it does not append a physical unit, planned quantity, or completion percentage
