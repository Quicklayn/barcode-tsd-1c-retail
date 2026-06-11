# User Rules

## Migrated content from a previous setup

<!-- start of migrated content -->
<!-- end of migrated content -->

## Project-specific rules

- This repository is a mixed 1C + native Android project. The default 1C-only
  language assumption from `AGENTS.md` applies only to 1C source areas.
- Treat `RT3/**` as the read-only baseline export of the 1C:Retail 3.0
  configuration unless the user explicitly asks to modify the baseline.
- New 1C development for the TSD integration should be designed for an
  extension-first workflow under `extension/**`; modifying `RT3/**` directly is
  a fallback that requires an explicit design decision.
- Android application code belongs under `android/**` and should use native
  Kotlin/Gradle with `minSdk=26` for Android 8.0 Oreo compatibility.
- API contracts between Android and 1C belong under `docs/api/**` and must stay
  synchronized with OpenSpec changes.
- OpenSpec is the only SDD workflow for this project. Do not add Spec Kit,
  `.specify/`, Memory Bank, TaskMaster, or another parallel SDD framework unless
  the user explicitly reopens the workflow decision.
