# Project Horizon — Foundation

## Development rules
- Preserve the original repository and upstream GPL notices.
- Separate package identity: com.pocketnexus.console (development: .debug).
- Development builds use the debug signing key; release signing requires a new privately held key. Never use the inherited public key, which remains in imported Git history.
- CI produces development artifacts only; no automatic release or force-push.
- The v1 settings backup remains the migration input. Never commit personal exports, credentials, or device identifiers.
- Hardware tuning and DSP require capability detection, reversible changes and device validation.

## Roadmap
1. Foundation, package and update isolation, library concurrency.
2. Console shell, focus, accessibility, themes and audio identity.
3. Retroid bridge, reversible performance profiles, PServer capability checks.
4. PC Connect encrypted control and host interoperability.
5. Media/cloud adapters, DSP, Bluetooth accessory support.
6. Obtainium import, storage, save synchronization and diagnostics.
7. Benchmarks, physical-device tests and release signing.

## Validation
The CI workflow builds ARM64 development APKs and runs existing pocket unit tests. No hardware compatibility or release readiness is asserted by this foundation commit.

## Shell milestone 1
Seven Horizon presets are available alongside the fifteen legacy presets. Preset matching now includes sound, layout and focus where specified. Shared motion respects Android's disabled-animations setting. These are initial presets on the existing renderer, not a completed redesign or new audio assets. Tests cover matching and legacy preservation.

## Search milestone
Global search augments the existing Home, Android and ROM index with all launch paths from UniversalLibrary. Mixed platform/title searches are supported; name hits rank above source-only hits. Search callbacks avoid destroyed activities. This indexes cached sources, not a live query to every store.

## Streaming milestone
Six opt-in connection profiles apply only resolution, FPS and Wi-Fi/USB bitrates. Existing codec, frame generation and controller settings remain untouched. Settings reflect manual overrides as Custom. Values are unbenchmarked starting points, not network guarantees or CPU/GPU profiles.

## Performance milestone 1
The device info panel reports instantaneous estimated battery-side power using public Android battery telemetry when available. Control Center prioritizes recognized ClusterTune and PULSE launcher activities. This is launcher integration only, not a PServer backend; no CPU/GPU or thermal settings are written.

## Update catalog milestone
Import Obtainium schema 1/2 exports through the Android document picker. The catalog stores only sanitized metadata and source host, not raw URLs, headers, regexes or credentials. It displays an imported snapshot; live resolution, release filters, signature verification, installation and scheduling remain separate milestones.

## Storage milestone
A read-only volume inventory shows Android-reported internal/removable storage, available/total space, read-only state, and selected ROM-root availability. The selected path is not changed by this panel. Multi-root indexing, drive identity persistence and game transfer remain future work.

## PC Connect milestone 1
Bounded, complete line framing rejects truncated replies and embedded newline commands. Read-only diagnostics distinguish reachable helper, pairing, PING latency, version and status. The Windows helper is unchanged and APC1 remains plaintext; use USB or trusted encrypted networking. TLS, host identity verification and the Vibepollo management adapter are future work.

## Audio milestone 1
Android-visible audio output inventory, sound/Bluetooth settings and optional Huawei AI Life handoff are exposed in the device panel and Settings. Existing media session controls now respect advertised capabilities and recheck them when pressed. No ANC, internal EQ, JamesDSP processing or privileged audio routing is implemented in this milestone.

## Controls and widgets milestone
Global Back/Guide hold duration is configurable, without changing stream controls or existing bindings. Quick Control offers three reorder presets preserving all six tiles. Two optional Home widgets expose battery-side power and internal free space / mounted removable volume count. Neither is automatically inserted into an existing shelf. Storage refresh is throttled. Per-game global bindings, free-form tile editing and deeper telemetry remain future work.

## CI diagnostics
CI explicitly installs Android SDK 36 and the pinned native tools. APK assembly and unit tests are separate steps; build logs and test reports are retained even on failure. Successful runs upload a development APK, not a release. The imported default branch may still be ci-logs; a repository administrator should select horizon-foundation before using manual workflow dispatch.

## Retroid Bridge milestone 1
Read-only hardware/firmware and sysfs frequency inventory, conservative Android thermal guidance, and existing tuning-tool handoff. CPU policies and GPU readings depend on app-visible kernel nodes. PServer remains unverified on the target firmware and no privileged operations are performed. References for future compatibility review: https://github.com/keiretrogaming/pulse and https://github.com/AurelioB/ClusterTune (no upstream source copied in this milestone).

## Settings migration milestone
Horizon exports version 2 and accepts original Pocket Nexus version 1. All recognized stores are decoded and type-checked before writing, device-bound widget IDs, old updater state, USB route cache and music URI are excluded. PC pairing, storage grants and Android widgets must be re-established. A write failure across separate SharedPreferences files is not fully transactional; arbitrary unknown preference names remain possible and import only trusted backups. No personal export is included in the repository.

## Smart streaming milestone
Connection check no longer assumes 150 Mbps from a wired interface. Single-host TCP timing, link indications and VPN presence produce a conservative starting point capped at 25 Mbps, with thermal/battery-saver reductions. Explicit confirmation applies resolution, FPS and ordinary bitrate, leaving USB bitrate, codec and frame generation untouched. This is a heuristic, not a bandwidth benchmark, live adaptation or direct/relay classifier. Existing per-game and dock settings may override it.

## Validation candidate — updater
Development builds reject update downloads and installation. Release downloads accept HTTPS on explicitly allowed GitHub hosts, enforce a 512 MiB bound and remove incomplete files. The staged APK must match the installed package, have a greater version code and the same current signing certificate set before being renamed and again before installation. Android remains the final APK verifier. Key rotation is deliberately unsupported. Full Android CI and real-device validation are still required.

## Alpha 1 CI baseline
The validation candidate passed its Android build and all 100 recorded unit tests, and PR #1 was merged into the development branch. Physical device validation remains open. See [device test checklist](HORIZON_ALPHA1.md).

## Alpha 2 UI candidate
Independent Home card density and corner preferences preserve existing presets; compact and large sizes apply to shelves and grids, with a short-screen guard. Animated key art is optional, and Home releases motion resources on pause. Full physical-device layout and focus checks remain required. Alpha 1 baseline is retained until this candidate passes CI.
