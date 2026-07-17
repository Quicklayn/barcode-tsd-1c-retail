---
description: "Barcode TSD project workflow: OpenSpec governance, mixed-stack ownership, quality gates, hooks, CI, and archive criteria."
alwaysApply: false
---

# Barcode TSD OpenSpec Workflow

Load this rule before changing any governed path:

- `android/**`
- `extension/**`
- `docs/api/**`
- `docs/testing/**/*.ps1`
- `scripts/**`
- `.githooks/**`
- `.github/workflows/**`
- `.codex/**`, `AGENTS.md`, `LLM-RULES.md`, or `USER-RULES.md`
- `package.json`, `package-lock.json`, or `openspec/config.yaml`

Pure human documentation and typo-only changes outside these paths may use the docs-fix path without a new change. `RT3/**` is always read-only.

## Required lifecycle

1. **Propose**: create or select one explicit active change. Read `openspec/README.md`, `openspec/specs/README.md`, `openspec/changes/README.md`, and `.codex/rules/sdd-integrations.md`. Complete proposal, delta specs, design, and tasks before implementation.
2. **Apply**: run `npm exec -- openspec instructions apply --change <name> --json`, read every returned context file, state locked decisions, and execute tasks in order. Mark a checkbox only after its acceptance check passes.
3. **Delegate**: use only agents needed by the task. `tsd-1c-developer` owns `extension/**`; `tsd-kotlin-developer` owns `android/**`; `tsd-reviewer` is read-only. The parent/orchestrator owns OpenSpec, API contracts, shared scripts, CI, and integration decisions unless a disjoint scope is assigned explicitly.
4. **Verify**: run strict OpenSpec validation and the relevant repository quality gate. Request reviewer work only after an implementation diff exists. Resolve blocking findings before completion.
5. **Archive**: require checked tasks, strict validation, full quality gate, and resolved review. Run `npm exec -- openspec archive <name> --yes`; manual moves, `--skip-specs`, and archive with warnings are forbidden. Validate the synchronized specs and keep the dated archive in Git history.

## Project commands

```powershell
# One-time or repeated workstation bootstrap
.\scripts\setup\Initialize-Development.ps1

# Fast local process/contract checks
.\scripts\quality\Invoke-QualityGate.ps1 -Mode Fast -DiffMode Working

# Hosted-runner-equivalent checks
.\scripts\quality\Invoke-QualityGate.ps1 -Mode Full -DiffMode Working

# 1C + Android end-to-end infrastructure check
.\scripts\quality\Invoke-QualityGate.ps1 -Mode Mvp -DiffMode Working
```

Git hooks use the same entry point: staged `Fast` on pre-commit and range `Full` with completed tasks on pre-push. Configure GitHub branch protection to require `Quality Gate`; local hooks are feedback, not a security boundary.

## Rules and skills updates

Update managed `comol/ai_rules_1c` files only through its official project-scoped installer and `.ai-rules.json`. Do not hand-edit managed upstream files. Keep TSD profiles, this rule, `USER-RULES.md`, and project documentation as project-owned overlays.

Managed OpenSpec skills are prompt guidance bundled by `comol/ai_rules_1c`; the root lockfile's OpenSpec CLI is the execution authority. Resolve any managed-skill reference to `content/rules/sdd-integrations.md` as the installed `.codex/rules/sdd-integrations.md`, and use current Codex tool equivalents for generic tool names.
