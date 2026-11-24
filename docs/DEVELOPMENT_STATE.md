# Horizon development state — 2026-11-24

Read HORIZON_SYSTEM_BLUEPRINT.md, HORIZON.md and HORIZON_FOUNDATION_GATE.md (on G01 candidate) before continuing. Integration branch horizon-foundation; default ci-logs is not integration. Inspect live refs and CI. Preserve all prior features, user data, approved distinct Horizon UI, Back/Guide mappings, Android RP6 and Windows Vibepollo. Work in substantial batches toward the next of 14 release gates, not one small PR per user request. Never force push, merge stale/failed CI, expose plaintext APC1 to public internet or require root.

## Verified baseline
PR #29 merged at 98d62c598634749a6da7249e50b6b10f812bee29: 193 Android tests (45462900041), 43 Windows checks (45462900133). Includes Hero/Control polish, provider policy, Windows dashboard and USB/log improvements.
PR #31 merged at b6467cc113b71e7c4466006f3035ae10168bc344: 201 Android test pass lines and APK build (45463784153). Actual PC-stream attempt observer, per-game preset menu, Device Info diagnostics. No new global session lock or privileged writes. Previous Windows changes retained; no new Windows validation in #31.

## Current G01 candidate
PR #32 adds best-effort rollback of touched settings on commit failure (three tests), explicit release version and private signing guard, Android 13 synthetic same-signer package upgrade CI. Rebasing onto merged #31 preserves all its source changes; only overlapping file was this checkpoint. Fresh integrated Android and emulator CI required. See HORIZON_FOUNDATION_GATE.md. No actual signer, private credentials, user data or release publishing. Full gate requires evidence and target-device validation. Never uninstall a user's old app to resolve signature mismatch.

## Next batch
1. Verify #32 exact latest CI (Android build/unit tests and emulator upgrade), fix failures before merge. Check release signing strategy and actual target migration/upgrade evidence. Do not mark G01 complete based on build alone.
2. Continue Nexus Core (provider identity, session/task ownership and profile restoration); prioritize structural Horizon UI redesign and screenshots, not mere polish.
3. Continue Windows Host secure authenticated transport, pairing revocation, desktop GUI and installer, Vibepollo telemetry; preserve existing USB/mic/library/power.
4. Follow remaining G01–G14 blueprint gates, with honest physical device limitations.

## Release progress
0/14 strict gates accepted; not 0% development. G01, G02, G03 and G08 in active development; other partial systems retained. Physical RP6/Windows verification remains outstanding.
