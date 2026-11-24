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


## Preserved user agreement — 2026-11-25 (Amman)
This section is a continuity contract, not a new roadmap or a declaration of implemented features.
- Independent Pocket Nexus 2.0 / Project Horizon console shell; leave original app and its repository usable, preserve features, bindings, settings and user data. Maintain the existing 14 gates above; do not restart.
- Primary setup: Retroid Pocket 6 / ARM64 / Android 13, internal 128 GB + SD 256 GB + occasional USB SSD 512 GB, Huawei FreeBuds 7i; Windows Core Ultra 7 / RTX 4060, Vibepollo (Apollo compatibility where applicable). Windows Host is a first-class deliverable.
- Controller-first and touch-accessible. Dedicated physical Back opens Nexus Control; B is normal back/cancel in shell and a game button in stream; Home during stream is Xbox Guide; preserve customizable M1/M2. App radial/Quick Switch must not intercept gameplay or replace the stream-specific menu. Verify physical mapping at final RP6 acceptance; never silently treat indistinguishable B events as dedicated Back.
- Original visual priorities: Game Cards, Hero, then Control Center; continue structural Home, Library, Connect, Media, System, game detail, settings, permission and overlay work, not palette-only changes. Keep existing layouts and settings available until replacements pass controller/touch, compact/wide, screenshot and reduced-motion checks. Theme/Sound Studio and Widgets 2.0 remain in scope.
- Preserve existing native/Android/Retro/PC/cloud clients and all launch paths. Session Manager, Game Hub, profiles, Quick Resume, search, game details, diagnostics and storage/save management must operate together. Distinguish local/external launches from an observed streaming session; do not claim universal suspend/resume.
- Windows scope: dashboard, Vibepollo manager, hardware monitoring, PC library, save/file transfer, power/display/device management, security, updater and installer. Secure authenticated host transport is mandatory before public exposure; plaintext APC1 is not safe for direct internet access.
- Root/Shizuku are optional. PServer, PULSE, ClusterTune and Calibrate-SoC are research/capability-gated targets; no privileged writes without authentication, thermal safeguards, journal, readback and rollback. JamesDSP/LibreBuds integration is likewise conditional, not guaranteed vendor control.
- Research streaming integrations Starwire, Moonlight X, KAST, PyroWave, VRR; data/updates RomM, Argosy, Ludusavi, Obtainium; Playnite interoperability. Evaluate actual feasibility, security, licensing and regression before any port. Additional candidate ideas from prior recap (Syncthing-Fork, GameNative/GameHub Lite, CHDroid, ES-DE and named convenience features) remain candidates for feasibility verification rather than confirmed ports.
- Work in substantial batches, with Android and Windows CI where relevant. User explicitly defers interim device testing until integrated delivery. Do not substitute CI for physical RP6/Vibepollo evidence or owner-held signing. Engineering milestone count and full gate acceptance count are distinct; use Alpha/Beta/RC/Stable only when criteria support the label.

## Continuation checkpoint
G01 engineering baseline passed at #43 (byte-identical ARM64 rebuild and migration checks); owner signing and physical upgrade remain open. G02 latest integrated #54 (fe9cb68cdf9f8758078d7c6f937fcf2236dc43c1), external shortcut discovery/dispatch ownership. #55 candidate guards ambiguous shortcut identity; separate pending-PC dedup candidate follows. Prioritize closing G02 session/task/profile ownership, with structural Shell and secure Host work in parallel, then the rest of the gates above. No interim owner tests. Full gate acceptance remains 0/14; engineering milestone count 1/14. This checkpoint supersedes the early snapshot above.
