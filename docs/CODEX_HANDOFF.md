# Codex handoff — Pocket Nexus 2.0 / Project Horizon

This is a repository-side continuity package, not a running Codex task. It preserves the existing project and avoids dependence on long ChatGPT conversations.

## Verified checkpoint
- Repository: alameergym-netizen/Pocket-nexus-2.0.
- Integration: horizon-foundation at 66fdc638fc23dbd4055565a99a2081c5f23201df after merging PR #57; CI 45499079025 succeeded on its exact tested head 01d10ad7d2deff20af0557f5d43d16b3ef51fdd4.
- #56 merged d1f7e6393c5e857656cc3d1536bc004bb067f800; CI 45498359630 passed APK build and unit tests.
- #58 (horizon-core-deferred-task-ownership, head f8ef17c89e9f00e010ff9eea2e3c5fee73d11fce) includes #57 and deferred Desktop/sign-in continuation guards. CI 45499274985 succeeded on that head before #57 merge; reconcile current base, inspect changes and obtain fresh exact combined CI before integration. Five new policy tests.
- #59 (horizon-core-discovery-ownership, head 4888e5164a811600437520da7d75e62b44d85ada) independently based on #56; foreground polling/discovery ownership, four pure tests. Foundation CI 45499663215 and Home UI CI 45499663141 running at handoff. Inspect latest outcomes, reconcile source and overlapping development-state docs, obtain integrated CI before merge.
- Engineering gate count 1/14; full acceptance 0/14. G02 remains open. No physical device validation has been performed for these changes. Review the current repository status rather than trusting this snapshot if newer commits exist.

## Existing roadmap (do not restart)
G01 Foundation; G02 Core; G03 Shell; G04 Gaming; G05 Stream; G06 Hardware; G07 Data/storage; G08 Windows Host; G09 Audio; G10 Connect; G11 Services; G12 Automation; G13 Optimization/recovery; G14 Release. The full blueprint is authoritative.

First goal: reconcile #58/#59, address regressions and remaining G02 acceptance criteria (provider capability semantics, explicit session/task ownership, stable game identity, profiles/settings, event ownership and duplicate-session prevention). Start structural Shell and secure Windows Host work in parallel if changes are independent. For Shell, deliver real controller/touch navigation, accessible screens, screenshot evidence, and functional Home/Library/Connect/Media/System/game details/settings/control overlays; not merely recoloring. Preserve existing working paths until replacement is tested.

## Hardware and use
Retroid Pocket 6 Android 13 ARM64; 128 GB internal, 256 GB SD, occasional 512 GB USB SSD; Windows Core Ultra 7 / RTX 4060, Vibepollo streaming host. User primarily streams and also uses indie/retro/local titles. Preserve all prior features and user settings. Separate launcher, stream and physical controller input. Known reported defects: B sometimes opens Control Center, wrong application radial during stream, stutter; prior code fixes do not prove device acceptance. Optional privileged hardware control only with capability validation, authentication, thermal safety, journal/readback/rollback. Windows host authentication and least privilege required; plaintext APC1 must not be exposed directly to internet. Research proposed integrations rather than blindly copying code; verify licensing.

## Suggested Codex goal
Continue Pocket Nexus 2.0 autonomously from this handoff. Reconcile verified open PRs and CI, finish G02 engineering criteria, then deliver G03 and subsequent gates in substantial tested batches according to the blueprint, in parallel where safe. Keep a living plan/checkpoint, run build and tests, fix failures, and produce reviewable changes. Do not ask the owner to repeatedly say continue or perform interim physical tests. Never conflate CI with physical verification. Report verified milestone progress, genuine blockers and next steps. Continue until the goal is complete or the execution budget/access requires stopping.

## Handoff branch handling
This branch is based on merged #57. Integrate these documentation-only files through normal review, or read them from this branch while doing implementation on branches based on current horizon-foundation. Do not accidentally revert newer integration commits. If GitHub mutation is unavailable in Codex, create a local patch/PR as supported and tell the owner what is still pending. Do not assume the prior chat's GitHub connector permissions transfer to Codex.
