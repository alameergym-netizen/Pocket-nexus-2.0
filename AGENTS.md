# Pocket Nexus 2.0 — Project Horizon

## Start here
Read `docs/CODEX_HANDOFF.md`, `docs/HORIZON_SYSTEM_BLUEPRINT.md`, `docs/DEVELOPMENT_STATE.md`, and the current GitHub PR/CI state before editing. The handoff branch is a starting point, not the integration branch. The integration branch is `horizon-foundation`; never develop on or overwrite the default `ci-logs` branch.

## Execution contract
- Continue substantial verified batches toward the existing 14-gate plan, without requesting repeated "continue" prompts. Work independently within the current goal, access, budget and safety limits.
- Inspect existing code before proposing replacements. Preserve user data, settings, migrations, existing clients, licenses, and controller mappings. Keep Android and Windows work in scope.
- Start from the latest verified integration commit; reconcile overlapping PRs and avoid duplicate work. Never force-push, bypass failed checks, expose secrets, or claim physical tests were run. Integrate only exact tested heads on the current base, where permissions allow; otherwise leave a reviewable PR and state the blocker.
- Use the existing CI workflows and repository build/test instructions as the source of truth. Build Android ARM64 development APKs and run unit/integration tests; use Windows checks for host changes and screenshot/UI tests for shell changes. Fix regressions rather than weakening tests.
- Retroid Pocket 6 physical validation, Vibepollo interoperability and owner-held release signing are final acceptance blockers, not reasons to stop unrelated engineering. Never publish or sign an official release without owner authorization.
- Back opens Nexus Control, B remains normal cancel/back in shell and gameplay input in stream, Home is Xbox Guide in stream, M1/M2 stay configurable. Application radial must not intercept the stream-specific menu. Physical mapping remains unverified.
- Update `docs/DEVELOPMENT_STATE.md` after verified milestones; record commit SHA, exact test evidence, open defects, next steps, and separate engineering versus full acceptance counts. Do not invent progress percentages or report planned features as implemented.
- If blocked, continue independent safe work if feasible. Stop on a genuine access/approval/budget limit or completion of the current goal. Report concise Arabic progress to the owner with verified changes and explicit limitations.
