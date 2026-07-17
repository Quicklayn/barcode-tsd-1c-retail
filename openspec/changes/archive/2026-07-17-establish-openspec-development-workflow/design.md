## Context

The repository is a mixed 1C extension and native Android project. OpenSpec, CI workflows, smoke scripts, and specialized TSD agents already exist, but each is invoked independently. The OpenSpec config is not valid for the pinned CLI because `context` is currently a YAML object instead of a string, local developers do not have a deterministic bootstrap command, and Git does not enforce the specification lifecycle before code reaches GitHub.

The workflow must work on Windows developer machines, GitHub-hosted Linux/Windows runners, and an optional self-hosted Windows runner with 1C:Enterprise and an Android emulator. The vendor baseline under `RT3/**` remains read-only.

## Goals / Non-Goals

**Goals:**

- Make OpenSpec the enforceable source of intent for governed repository changes.
- Use one pinned OpenSpec version locally and in CI.
- Reuse the same validation entry points from developer commands, hooks, and GitHub Actions.
- Keep fast feedback before commit and broader verification before push/merge.
- Make subagent ownership derive from the active change and its tasks.
- Preserve project-specific agent/rule overlays when upstream `1c-rules` is updated.

**Non-Goals:**

- Change barcode lookup behavior, API payloads, Android UI, or 1C metadata.
- Require a 1C license, test infobase, web server, or emulator for every hosted CI run.
- Introduce another SDD framework or a custom OpenSpec schema.
- Make Git hooks a security boundary; CI remains the non-bypassable merge gate.

## Decisions

### Use the built-in `spec-driven` schema

`openspec/config.yaml` will set `schema: spec-driven`, provide a valid multiline string under `context`, and add per-artifact rules. A custom schema was rejected because the existing `proposal -> specs/design -> tasks -> apply` graph already matches the project and is supported by the installed OpenSpec skills.

### Pin the CLI in the repository

The root `package.json` and lockfile will pin `@fission-ai/openspec` to `1.5.0`. Bootstrap uses `npm ci`; hooks and CI call the local binary through npm scripts. A global CLI was rejected because it makes validation depend on workstation state and can drift from CI.

The OpenSpec skills delivered by `comol/ai_rules_1c` are managed prompt guidance and may carry the bundle generator version recorded by that installer. They do not select the executable CLI. Project overlays map their source-only paths/tool names to Codex and require every executable OpenSpec command to use the root lockfile version through `npm exec -- openspec`.

### Use one PowerShell quality-gate entry point

`scripts/quality/Invoke-QualityGate.ps1` will expose three modes:

- `Fast`: OpenSpec strict validation, PowerShell parsing, contract consistency, and OpenSpec coverage for the selected diff.
- `Full`: all fast checks plus Android assemble, JVM unit tests, and lint.
- `Mvp`: all full checks plus the existing end-to-end MVP smoke that recreates the temporary infobase, clears Android app data, and requires local 1C and emulator infrastructure.

The coverage check will bind each governed diff to exactly one active change or newly created archive. The corresponding `tasks.md` must change in the same diff; proposal, design, delta specs, and task content are read from the exact working/index/commit snapshot being checked. A current-spec edit alone, an existing archive, or a second unrelated change cannot satisfy coverage. New archive evidence additionally requires synchronized current specs for every delta capability. Changes under `RT3/**` are always rejected. Complete-task enforcement is enabled for pre-push and CI, while pre-commit permits incomplete active tasks so incremental work remains possible.

### Version hooks and install them through Git config

`.githooks/pre-commit` runs `Fast` against staged files and rejects any unstaged governed path, preventing related working-copy files from masking the index snapshot. `.githooks/pre-push` requires a clean worktree and the pushed local object to equal current `HEAD`, then runs `Full` for every non-deletion ref range and requires completed OpenSpec tasks. A new branch is compared from its merge base with `origin/main`. This deliberately simple restriction avoids temporary worktrees while keeping local feedback aligned with the checked snapshot. `scripts/setup/Initialize-Development.ps1` installs dependencies and sets `core.hooksPath=.githooks` idempotently.

Git's explicit `--no-verify` escape hatch remains available for emergencies, while CI reruns the mandatory checks and cannot be bypassed by the commit command.

### Split CI by runner capability

A single required `Quality Gate` workflow will run on every pull request and push to `main`:

- Windows: fast process/contract gate and diff coverage.
- Ubuntu: pinned OpenSpec validation and Android build/test/lint.

The end-to-end MVP smoke will be a manual workflow for a dedicated labelled self-hosted Windows runner and will compare the selected ref with `origin/main`. This keeps hosted checks deterministic without pretending that 1C:Enterprise is available on GitHub-hosted runners. The runner keeps only its local ignored `RT3/**` baseline across checkout; build and smoke commands recreate their own outputs.

### Keep upstream rules managed and project overlays separate

The official `comol/ai_rules_1c` installer will update managed rules and skills in project scope. `USER-RULES.md`, `.codex/rules/tsd-openspec-workflow.md`, and `.codex/agents/tsd-*.toml` remain project-owned overlays. Managed upstream files will not be edited manually.

### Make agent context dynamic

The orchestrator will select the active OpenSpec change, delegate disjoint ownership, and keep `tasks.md` current. The 1C and Kotlin agents will read the change paths supplied by the orchestrator plus the current API contract. The reviewer remains read-only and checks only after an implementation diff exists. Hardcoded references to the archived MVP change and hardcoded feature scope will be removed from agent profiles.

### Archive only after evidence is complete

Before archive, every task must be checked, strict OpenSpec validation must pass, the full quality gate must pass, and review findings must be resolved. Archive is executed only by the pinned local CLI (`npm exec -- openspec archive <name> --yes`), without `--skip-specs` or manual directory moves. The CLI synchronizes the delta into `openspec/specs/**`; the archived change remains auditable in Git history.

## Risks / Trade-offs

- [Pre-push duration increases because Android verification runs] -> Keep pre-commit fast and reserve Android build/test/lint for pre-push and CI.
- [Hooks are not installed automatically by Git clone] -> Provide one idempotent bootstrap command and verify `core.hooksPath` during setup.
- [OpenSpec coverage is path-based and cannot prove semantic quality] -> Combine it with strict CLI validation, explicit artifact rules, task completion, review, and CI.
- [A self-hosted full-smoke runner may be unavailable] -> Keep the same `Mvp` command runnable locally and make the GitHub workflow manual.
- [Upstream rule updates can introduce broad diffs] -> Use the official manifest-aware installer, preserve user-modified files, and review the resulting diff before commit.

## Migration Plan

1. Update managed `1c-rules` content using the official project-scoped installer.
2. Fix OpenSpec configuration and add the pinned root toolchain.
3. Add quality scripts, hooks, and bootstrap.
4. Replace separate hosted workflows with the unified quality gate and add the manual full-smoke workflow.
5. Update TSD agent overlays and workflow documentation.
6. Run bootstrap, strict validation, fast/full gates, hook smoke tests, and an independent read-only review.
7. Complete tasks, sync the new capability spec, and archive the change.

Rollback is a Git revert of this isolated change. Existing application binaries and 1C extension sources are unaffected.

## Open Questions

None.

## Context sources

- Repository: current OpenSpec config/spec layout, GitHub Actions, smoke scripts, TSD agent profiles, and `1c-rules` manifest.
- Official OpenSpec customization documentation: project config supports `schema`, string `context`, and per-artifact `rules`.
- Official `comol/ai_rules_1c` installation protocol: project-scoped manifest-aware updates preserve project-owned files.
