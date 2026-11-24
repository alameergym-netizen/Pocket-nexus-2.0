# Horizon — full-system blueprint and release gates

This document records the expanded system-layer direction, preserving the original Horizon roadmap and existing implementation. It is a development checklist, not a claim of completion or a new Android OS. Retroid Pocket 6 / ARM64 / Android 13 and Vibepollo on Windows are the primary validation targets. Preserve original app, package isolation, user settings, controller mappings, GPL notices and existing streaming engine. No compulsory root.

## Release gates (each counts once only after all acceptance criteria pass)
- [ ] G01 Foundation: reproducible build, isolated package, migration and upgrade without loss; fixed private release signing strategy.
- [ ] G02 Core: provider capabilities, explicit session lifecycle, stable launch identity, profiles, settings, event and task ownership; no duplicate active sessions.
- [ ] G03 Shell: approved original Horizon visual language, actual screenshots for Home, Library, Connect, Media, System, game details, settings, control/overlays; controller and touch navigation; accessibility and reduced motion.
- [ ] G04 Gaming: unified game/launch-target identity, library collections, per-game settings, Quick Resume semantics for PC vs local; input calibration and Back/Guide physical verification.
- [ ] G05 Stream: Vibepollo compatibility, codec/renderer fallback, reconnect, LAN/remote/USB tests and no regression against baseline. Investigate Starwire/Moonlight X/KAST/PyroWave/VRR before porting.
- [ ] G06 Hardware: read-only diagnostics and capability-gated PServer/optional privileged controls; authenticated backend, journal, readback, rollback, thermal safety; RP6 tests. Investigate PULSE, ClusterTune, Calibrate-SoC.
- [ ] G07 Data: simultaneous internal/SD/USB libraries, stable source identity, disconnect handling, verified file transfer, save backup/restore and conflict protection; consider RomM/Argosy/Ludusavi.
- [ ] G08 Host: secure authenticated Windows helper, least privilege, explicit allowed commands, pairing revocation; Vibepollo/Playnite interoperability and Windows integration tests.
- [ ] G09 Audio: streaming DSP and actual route diagnostics, safe presets, audio performance tests; LibreBuds FreeBuds 7i and JamesDSP research, never assume vendor controls or system-wide capture.
- [ ] G10 Connect: provider registry, existing official/browser/cloud choices preserved; Xbox/PlayStation remote adapters where supported; verify login and session lifecycle without password capture.
- [ ] G11 Services: update catalog and verified APK installation, media, notification center, capture/gallery, widgets; permission-aware and controller-first. Obtainium and supported media integrations.
- [ ] G12 Automation: opt-in profile rules, resource ownership, conflict ordering safety > manual > game > device > defaults, recoverable changes; no automatic privileged writes without validation.
- [ ] G13 Optimization and recovery: startup/frame/memory/power benchmarks, sleep/wake, crash recovery, safe mode and Android launcher escape, migration and screenshot regression.
- [ ] G14 Release: integrated Android/Windows tests, physical RP6 matrix, signed stable APK upgrade test and documented recovery, security and license review.

Progress: number of fully accepted gates / 14. Partial milestones tracked separately; no credit for a successful build alone, pending PR or untested hardware. At initial checkpoint all gates remain open under these stricter full-release criteria; this does not erase earlier completed features. Review gates at each integration and link evidence. Optional unsupported integrations must be explicitly dispositioned and not silently presented as supported.

## Delivery rules
Maintain horizon-foundation as integration baseline; independently tested branches, pull requests, verified combined CI, preserve previous build and preferences. Do not merge stale or failing candidates. Keep legacy functionality available until replacement is validated. No direct internet exposure of plaintext APC1; do not reuse inherited public signing key. No arbitrary dynamic executable plugins. Never put credentials, private settings, ROMs or BIOS in repository.

## Current checkpoint
PR #20 Home visual pass and PR #21 card loading pass merged; PR #22 Hero retargeted for integrated CI; PR #23 Control visual independently passed CI on earlier base, not yet merged. These are incremental polish, not the approved full shell redesign. First Core milestone is a pure session transition policy, not yet connected to launch or hardware paths.
