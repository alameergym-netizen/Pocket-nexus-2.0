# Horizon development state — 2026-11-24

Read docs/HORIZON_SYSTEM_BLUEPRINT.md and docs/HORIZON.md first. Actual integration branch: horizon-foundation (not ci-logs). Inspect live refs, PRs, exact CI results and artifacts before continuing. Treat this document as a dated checkpoint, not live status. Preserve user data, original features, original approved Horizon visual identity, controller mappings (Back Control; Home while streaming Xbox Guide), ARM64 Android 13 RP6 and Windows Vibepollo compatibility. Do not stop at a single small PR: work in coherent batches with verified checkpoints. Do not force push, merge stale/failed CI, expose plaintext APC1 to public networks or require root.

## Verified baseline
PR #29 merged to foundation commit 98d62c598634749a6da7249e50b6b10f812bee29. Integrated Android CI 45462900041: APK build, 193 passed test lines; Windows CI 45462900133: 43 successful checks. Contains #22 Hero, #23 Control, #25 provider classification, #26 Windows desktop dashboard, #27 USB non-destructive mapping and PIN log redaction, #28 initial integration. #24 session policy and blueprint already merged. These are not final redesign, physical device validation, or encrypted connection. Original PRs superseded by #29; close only after confirming merged content.

## Current candidate
PR #31 includes #30 stream observation, game-specific preset menu and Device Info last-local-attempt status, four observer and four preset tests. Updated on exact merged #29 base without path overlap. Requires new combined Android CI; unchanged Windows content retains earlier evidence but final release still needs Windows/RP6 integration. No actual global session lock, automatic hardware controls, or cross-process session recovery. Bitrate preset applies to every link of that game; three existing preference fields changed only after confirmation. 

## Next batch
1. Verify #31 current merge ref, APK and unit tests; merge only if successful. Close #30 as superseded after merge. Update checkpoint and offer tested artifact when available.
2. Prioritize actual new Horizon Shell design and screenshot validation, including Home, library, stream, game detail and Control Center; not cosmetic tweaks. Preserve existing functionality. 
3. Expand unified provider/session identity and profile engine, including audio/storage and safe per-session restoration. 
4. Windows: encrypted authenticated transport, pairing revocation and protected credential storage, full UI/installer, Vibepollo capabilities and telemetry. Preserve USB/mic/power/library; actual Windows and RP6 tests.
5. Continue all remaining blueprint gates.

## Release progress
0/14 strict full-release gates accepted. This is not 0% development effort. Existing partial functionality and verified tests retained. Do not infer progress percentage or hardware compatibility from build success.
