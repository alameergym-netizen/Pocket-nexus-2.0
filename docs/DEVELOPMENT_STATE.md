# Horizon development state — 2026-11-24

Read docs/HORIZON_SYSTEM_BLUEPRINT.md, HORIZON.md and HORIZON_FOUNDATION_GATE.md. Integration branch horizon-foundation, not ci-logs. Inspect live PRs and exact CI before any merge. Preserve existing features, user data, original Horizon design, Back/Guide mapping, Android RP6 and Windows Vibepollo. Develop in substantial batches toward next release gate. Never force push, merge stale CI, expose plaintext APC1 to public internet or require root.

## Verified foundation
#29 merged at 98d62c598634749a6da7249e50b6b10f812bee29: 193 Android tests and 43 Windows checks. #31 merged at b6467cc113b71e7c4466006f3035ae10168bc344: 201 Android tests, real PC-stream observation and game presets. #32 merged at 1a645d5629887307b0ff22c68437ac6a5eac86cc: 204 Android tests (45464842745), Android 13 x86_64 same-package/same-signer synthetic upgrade retained three preference files and private file (45464842534). Restore rollback best-effort, explicit release signing guard/version code. Actual private release signer and RP6 migration/upgrade still unverified.

## Current candidate
#33 independently passed 201 Android tests (45465197549), 43 host checks + 6 APK preflight tests and deterministic Host ZIP packaging (45465197766). No actual APK installation performed by preflight; no signed Windows installer or secure transport. Reconcile onto merged #32, with fresh integrated Android and Windows CI. Do not claim full Foundation until owner-held signer and physical migration/upgrade evidence. No private keys or passwords in chat or Git.

## Next work
1. Verify combined #33 and merge only fresh success; provide exact tested APK and Windows archive links.
2. G01: owner privately establishes release signer, verifies identity and backup/migration on target RP6; do not uninstall original or downgrade to resolve mismatch. Assess build reproducibility separately.
3. Work on other software gates in parallel while physical acceptance is blocked: original distinct Horizon Shell layout/screenshot tests, Core provider/session identity and profile ownership, secure Windows connection and pairing, data and storage integration. Follow full blueprint. No claim of hardware support without evidence.

## Release gates
0/14 fully accepted. G01 software work substantially advanced; physical/owner verification outstanding. This is not 0% development effort. Windows and Android equal release requirements.

## Latest batch
#33 merged at 3aa49d24c65513558ff55ab114f319136c9541f9 after combined Android 204 tests (45466382903), Windows 49 checks and verified ZIP (45466383121). #34 actual release signing CI first debug run failed downloading Gradle, rerun requested; release guard passed and actual release tests still running at this checkpoint. #35 new responsive Control workspace (split/stack, scroll, controller escape paths and touch adjustment) plus Android 13 UI screenshot/focus workflow. Reconciled onto #33 baseline; CI required. This is structural Shell work but not completed visual redesign. G01 remains 0/14 pending owner key and physical RP6 migration. Continue Shell and Core, plus secure Windows Host, while waiting for physical verification.

## Physical evidence and latest merged baseline
#35 merged at a46bcb5e084f7affef945a2053ee494980a1a58d: 208 Android tests (45467574288), Android 13 wide/compact Control UI controller navigation and screenshots (45467574480). #34 initial timeout rerun passed 204 tests (45466541990); disposable release signing and wrong-signer rejection passed (45466542105) on older base. Reconcile #34 onto #35 and rerun relevant CI before merge. User RP6 recording reviewed; see docs/HORIZON_RP6_PHYSICAL_2026-11-24.md. Existing Home, settings, old Control, discovery and Bluetooth handoff observed. No physical migration, exact APK identity, streaming, release key or new workspace verification. G01 remains 0/14; preserve all partial evidence. No personal video or identifiers in public repo.
