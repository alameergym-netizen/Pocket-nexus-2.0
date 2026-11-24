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
