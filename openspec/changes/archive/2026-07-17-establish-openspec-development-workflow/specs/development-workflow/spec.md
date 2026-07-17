## ADDED Requirements

### Requirement: OpenSpec governs implementation changes
The project MUST bind every Git diff that changes governed implementation, contract, automation, agent, rule, or pipeline paths to exactly one OpenSpec change. The same diff MUST update that change's `tasks.md`. Evidence MUST be a complete active change under `openspec/changes/<change>/` or a newly created synchronized archive plus the matching current specification updates; an existing archive or current specification alone MUST NOT satisfy coverage.

#### Scenario: Governed change includes specification evidence
- **WHEN** a diff changes a governed path and updates one active change's task evidence while all required artifacts exist in the checked snapshot
- **THEN** the OpenSpec coverage gate binds the diff to that change and passes

#### Scenario: Governed change bypasses OpenSpec
- **WHEN** a diff changes a governed path without updating one active or newly archived change's task evidence, or attempts to reuse an existing archive
- **THEN** the OpenSpec coverage gate fails with the governed paths listed

#### Scenario: Archive synchronizes capabilities
- **WHEN** a governed diff uses newly archived change evidence
- **THEN** the gate requires a current `openspec/specs/<capability>/spec.md` update for every delta capability in that archive

#### Scenario: Vendor baseline is modified
- **WHEN** a diff contains a path under `RT3/`
- **THEN** the OpenSpec coverage gate fails regardless of other specification evidence

### Requirement: Artifacts are executable without follow-up decisions
Each non-trivial change MUST contain a proposal, delta specification, design, and task list that resolve scope, ownership, validation, risks, and rollback before implementation begins. The final artifacts MUST contain no routine decision deferred to the apply phase.

#### Scenario: Change is ready for apply
- **WHEN** OpenSpec reports all apply-required artifacts as complete and the design has no unresolved routine decision
- **THEN** an agent can implement every task from the artifacts without asking the user for naming, placement, library, ownership, or verification choices

### Requirement: OpenSpec tooling is reproducible
The repository MUST pin the OpenSpec CLI version in a lockfile and MUST expose a project command that performs strict validation without relying on a globally installed CLI.

#### Scenario: Fresh workstation bootstrap
- **WHEN** a developer runs the repository bootstrap on a workstation with supported Node.js and Git
- **THEN** dependencies are installed from the lockfile, Git hooks are configured, and strict OpenSpec validation can run from the project-local toolchain

### Requirement: Local Git hooks provide staged verification
The repository MUST version a pre-commit hook that runs the fast quality gate against the exact staged snapshot and a pre-push hook that runs the full hosted-equivalent quality gate against every non-deletion ref range being pushed. Hook installation MUST be idempotent.

#### Scenario: Commit has an invalid spec or script
- **WHEN** a developer commits staged changes containing an invalid OpenSpec artifact, invalid PowerShell syntax, a contract inconsistency, or missing OpenSpec evidence
- **THEN** the pre-commit hook rejects the commit

#### Scenario: Push has incomplete tasks or Android regression
- **WHEN** a developer pushes governed changes with incomplete OpenSpec tasks or failing Android build, JVM tests, or lint
- **THEN** the pre-push hook rejects the push

#### Scenario: Bootstrap is repeated
- **WHEN** the development bootstrap runs more than once
- **THEN** the same project-local hook path remains configured without duplicate files or settings

### Requirement: CI uses the repository quality gates
GitHub Actions MUST run strict OpenSpec validation, OpenSpec diff coverage, task completion checks, PowerShell parsing, contract consistency, and Android build/test/lint for pull requests and pushes to `main`. CI MUST install dependencies from lockfiles and MUST not depend on global developer tooling.

#### Scenario: Pull request passes hosted checks
- **WHEN** all OpenSpec, process, contract, and Android checks pass for a pull request
- **THEN** the required `Quality Gate` workflow succeeds

#### Scenario: Full MVP infrastructure is unavailable on hosted runners
- **WHEN** GitHub-hosted runners do not provide 1C:Enterprise or an Android emulator
- **THEN** hosted checks still run deterministically and the end-to-end MVP smoke remains available through the same repository command on a labelled self-hosted Windows runner or local workstation

### Requirement: Subagents follow active change ownership
The TSD orchestrator MUST select the active OpenSpec change, delegate 1C and Android work to disjoint owners, keep task state synchronized, and request a read-only review only after an implementation diff exists. Developer and reviewer profiles MUST derive requirements from the active change, current specs, and API contract rather than a hardcoded archived change.

#### Scenario: Mixed-stack change is implemented
- **WHEN** an active change contains both 1C and Android tasks
- **THEN** the orchestrator assigns `extension/**` to the 1C developer, `android/**` to the Kotlin developer, retains integration artifacts centrally, and runs reviewer checks after integration

#### Scenario: Single-stack change is implemented
- **WHEN** an active change affects only one implementation stack
- **THEN** the orchestrator delegates only that stack and does not create unnecessary parallel work

### Requirement: Managed rules and project overlays remain updateable
Upstream `comol/ai_rules_1c` rules and skills MUST be updated with the official project-scoped installer and its manifest. Project-specific rules, user configuration, memory, and TSD agent overlays MUST remain preserved as project-owned files.

#### Scenario: Managed rules are updated
- **WHEN** the official installer runs in update mode from the project root
- **THEN** unchanged managed files receive upstream updates, user-modified managed files are reported and preserved, and project-owned overlays remain intact

### Requirement: Change completion is evidence-based
A change MUST NOT be archived until every task is checked, strict OpenSpec validation passes, the full quality gate passes, and all blocking review findings are resolved. The archive operation MUST synchronize delta specifications into the current specification set.

#### Scenario: Completed change is archived
- **WHEN** all completion evidence exists and archive is invoked
- **THEN** the delta is synchronized into `openspec/specs/**` and the change is moved to the dated archive with completed tasks retained

#### Scenario: Change has incomplete work
- **WHEN** any task remains unchecked or a required quality/review gate fails
- **THEN** the change remains active and is not treated as complete

## Context sources

- Local repository workflow, hook, CI, and subagent gaps identified from tracked project files.
- Official OpenSpec project configuration and spec-driven lifecycle documentation.
- Official `comol/ai_rules_1c` project-scoped update and file-ownership protocol.
