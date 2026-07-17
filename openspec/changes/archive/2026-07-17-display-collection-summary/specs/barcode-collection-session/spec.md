## ADDED Requirements

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

## Context sources

- `CollectionSession.kt` defines unique lines and exact three-decimal quantities; `MainActivity.kt` renders every restored and mutated session through one path.
- 1C MCP checks were skipped because this Android-only requirement does not reference or change 1C behavior.
