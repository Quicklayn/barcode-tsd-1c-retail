## 1. Update managed agent tooling

- [x] 1.1 Run the official `comol/ai_rules_1c` project-scoped installer in update mode; verify `.ai-rules.json` advances and project-owned `USER-RULES.md`, `memory.md`, and `.codex/agents/tsd-*.toml` remain preserved.
- [x] 1.2 Run the upstream installer doctor and verify the OpenSpec propose/apply/archive/explore skills plus required 1C rules/skills are present under `.codex/**`.

## 2. Make OpenSpec deterministic

- [x] 2.1 Rewrite `openspec/config.yaml` to valid OpenSpec 1.5 syntax with `schema: spec-driven`, string context, and per-artifact project rules; verify CLI instructions no longer report an invalid context field.
- [x] 2.2 Add root `package.json`, `package-lock.json`, and the `node_modules/` ignore rule with `@fission-ai/openspec` pinned to `1.5.0`; verify `npm ci` and `npm run openspec:validate` pass.

## 3. Implement repository quality gates

- [x] 3.1 Add `scripts/quality/Test-OpenSpecCoverage.ps1` to classify staged/range diffs, reject governed changes without OpenSpec evidence, reject `RT3/**`, and optionally reject unchecked tasks; verify positive and negative fixture-free invocations.
- [x] 3.2 Add `scripts/quality/Invoke-QualityGate.ps1` with `Fast`, `Full`, and `Mvp` modes that reuse existing contract and smoke scripts; verify PowerShell parsing and `Fast` mode pass.
- [x] 3.3 Add `scripts/setup/Initialize-Development.ps1` to run `npm ci`, configure hooks idempotently, and execute the fast gate; verify repeated setup keeps `core.hooksPath=.githooks`.

## 4. Add versioned Git hooks

- [x] 4.1 Add `.githooks/pre-commit` and `.githooks/pre-push` that invoke staged fast and range full gates respectively; set their executable Git mode.
- [x] 4.2 Install the hooks in the current clone and execute both hook entry points in a safe smoke mode; verify failures propagate as non-zero exit codes.

## 5. Align continuous integration

- [x] 5.1 Replace `.github/workflows/openspec.yml`, `smoke-scripts.yml`, and `android.yml` with `.github/workflows/quality-gate.yml` that installs lockfile dependencies and runs process/contract and Android gates on every pull request and push to `main`.
- [x] 5.2 Add `.github/workflows/mvp-smoke.yml` as a manual workflow for a labelled self-hosted Windows runner using `Invoke-QualityGate.ps1 -Mode Mvp`.
- [x] 5.3 Validate workflow YAML structure and run every locally available command used by hosted jobs.

## 6. Align agent orchestration and project rules

- [x] 6.1 Add `.codex/rules/tsd-openspec-workflow.md` and reference it from `USER-RULES.md` for governed changes; verify every referenced path exists and no parallel SDD workflow is introduced.
- [x] 6.2 Update `.codex/agents/tsd-orchestrator.toml`, `tsd-1c-developer.toml`, `tsd-kotlin-developer.toml`, and `tsd-reviewer.toml` to use the active change and current specs/contracts instead of the archived MVP path or hardcoded feature scope.
- [x] 6.3 Verify the orchestrator, 1C, Kotlin, and reviewer profiles have disjoint write ownership and a complete propose/apply/verify/archive handoff.

## 7. Document and verify the workflow

- [x] 7.1 Add `docs/development/openspec-workflow.md` and update `README.md` plus `openspec/README.md` with bootstrap, daily commands, hook behavior, CI gates, rules update, and recovery instructions; verify all local links and commands resolve.
- [x] 7.2 Run strict OpenSpec validation, PowerShell syntax checks, contract consistency, Android assemble/unit/lint, and the full local quality gate; record any infrastructure-only MVP smoke limitation explicitly.
- [x] 7.3 Run a read-only TSD reviewer against the final diff, resolve all blocking findings, and confirm no application behavior or `RT3/**` content changed.
- [x] 7.4 Mark all tasks complete, synchronize `development-workflow` into `openspec/specs/**`, validate again, and archive the change with its evidence retained.

## Context sources

- `design.md` decisions and `specs/development-workflow/spec.md` acceptance scenarios.
- Existing repository scripts, workflows, agent profiles, and official upstream installation/customization guidance.
