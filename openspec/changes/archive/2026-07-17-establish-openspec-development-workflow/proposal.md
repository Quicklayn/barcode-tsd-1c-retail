## Why

The repository already contains OpenSpec artifacts, AI-agent rules, and separate CI checks, but they are not connected into one enforceable development lifecycle. The current setup allows implementation changes to bypass an OpenSpec change, relies on ad hoc local commands, and contains project agents that are tied to the archived MVP change.

## What Changes

- Make the built-in `spec-driven` OpenSpec schema the explicit project default and add project-specific artifact rules.
- Pin the OpenSpec CLI in the repository and expose stable local validation commands.
- Add a repository quality gate that validates OpenSpec, PowerShell scripts, cross-stack contracts, and Android code at the appropriate stage.
- Add versioned Git hooks with an idempotent installer: fast checks before commit and the full hosted-runner-equivalent gate before push.
- Require governed implementation and pipeline changes to include an OpenSpec change or archived change update in the same diff.
- Align the TSD orchestrator, 1C developer, Kotlin developer, and reviewer profiles with the active OpenSpec change instead of the archived MVP path.
- Consolidate GitHub Actions around the same commands used locally and keep the platform-dependent end-to-end MVP smoke as an explicit Windows/self-hosted or local gate.
- Document the `propose -> apply -> verify -> archive` workflow and the bootstrap/update procedure for project rules and skills.

## Capabilities

### New Capabilities

- `development-workflow`: Defines the mandatory OpenSpec lifecycle, agent ownership, local quality gates, Git hooks, CI behavior, and completion criteria for repository changes.

### Modified Capabilities

None. The product lookup behavior remains unchanged.

## Impact

- OpenSpec configuration and a new workflow specification under `openspec/**`.
- Project-scoped AI rules and TSD subagent profiles under `.codex/**` and `USER-RULES.md`.
- Root development tooling, validation scripts, and versioned hooks.
- GitHub Actions workflows and developer documentation.
- No changes to `RT3/**`, `extension/**`, Android application behavior, or the API payload contract.

## Context sources

- Repository state: `openspec/config.yaml`, `.github/workflows/*.yml`, `.codex/agents/tsd-*.toml`, `.codex/rules/sdd-integrations.md`, and `.ai-rules.json`.
- Upstream guidance: OpenSpec project configuration/customization and `comol/ai_rules_1c` installation/update protocol.
