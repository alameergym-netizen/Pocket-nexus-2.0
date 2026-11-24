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
