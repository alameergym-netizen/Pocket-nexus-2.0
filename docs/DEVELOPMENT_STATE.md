# Horizon development state — 2026-11-24

## Resume here
Read docs/HORIZON_SYSTEM_BLUEPRINT.md and docs/HORIZON.md first. Integration branch: horizon-foundation (not repository default ci-logs). Inspect live PRs, actual branch head and workflow results before editing; this file is a dated checkpoint, not live status. Preserve original application, all existing capabilities and user data. Android RP6 and Windows Nexus Host are equal release requirements. Primary host: Vibepollo. Back opens Nexus Control (in-stream menu while streaming); Home during streaming sends Xbox Guide. Preserve existing bindings. No force pushes or untested merges.

## Verified baseline
Foundation b80bbeb21112a99cd14c31292584058512b34a54 includes merged PR #24 session state policy and full-system blueprint. CI 45461259856 passed (five session tests). Game cards #21 and Home #20 already merged. Session policy is not yet wired into runtime.

## Integrated candidate
This branch combines exact source blobs from independent successful candidates #22 Hero (run 45461431168), #23 Control (45458089143), #25 provider classification (45461504280), #26 Windows Dashboard (Android 45461786231 and Windows 45461786251, 38 checks). Their changed paths do not overlap each other or newer foundation changes. All require new integrated Android and Windows CI before merge. Keep original PRs until integration is confirmed; do not merge them separately afterward. No claim of completed shell redesign or verified desktop GUI.

PR #27 USB/privacy: Windows CI 45462100156 and Android CI 45462100192 passed independently. This successor branch reconciles the exact USB/log changes with the dashboard from #28, preserving all eleven additional Windows checks. Fresh integrated Android and Windows CI required. Legacy APC1 remains plaintext; no public internet exposure. Old log/status PIN and token storage remain outstanding.

## Next work in order
1. Verify integrated candidate CI and foundation head before merge.
2. Verify successor candidate with #27 and dashboard combined; do not merge stale results. Reconcile against current foundation if #28 lands first.
3. Connect session policy/provider classification to confirmed stream lifecycle with failure and stale-callback handling, then other providers. External activity launch is not confirmation of gameplay.
4. Build profile policy and full Horizon design system; capture actual screenshots. Do not treat current polish as final redesign.
5. Windows Host: secure pairing/transport, real desktop dashboard validation, Vibepollo integration and installer; continue alongside Android.
6. Track remaining G01–G14 gates in the blueprint. Physical RP6 and Windows tests are required.

## Release progress
0/14 fully accepted gates, not 0% of development effort. Partial foundation, core, shell, gaming, stream, hardware, data, host, audio, connect, services. Automation, optimization/recovery and release remain open. Update this checkpoint after verified integration, retaining links to evidence.

## G01 candidate
Foundation release-gate work adds rollback-on-commit-failure, explicit release version/signing guard and Android 13 synthetic package upgrade CI. See docs/HORIZON_FOUNDATION_GATE.md. PR #31 remains independent; combine only with fresh CI. No private key is stored or requested in chat. Full release gate remains open pending validation.
