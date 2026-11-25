# Pocket Nexus 2.0 feature acceptance

This ledger tracks the 153 criteria in the expanded completion specification against the [14-gate blueprint](HORIZON_SYSTEM_BLUEPRINT.md). The integration baseline is #101 at `c274713c51858643c61655fbb149a780fde544e3`. Imported audit statuses describe the supplied snapshot at #95. They are preserved as history. Evidence in this ledger applies to the named path or test, with the remaining scope stated where known.

P0 is core or release-critical, P1 is original product scope, and P2 is optional. The inherited audit uses V for automated verification, I for implementation evidence, P for partial, R for research, U for insufficient evidence, and H for hardware, signing or provider acceptance. An unmapped row remains open for a source audit or a targeted test. When adding evidence, record the tested commit, code paths, exact CI run and outstanding conditions. Use the [final physical matrix](HORIZON_FINAL_PHYSICAL_ACCEPTANCE.md) for device and owner signing results.

The [client and upstream registry](CLIENTS_AND_SOURCES.md) tracks provenance and unresolved upstream identities. The [development state](DEVELOPMENT_STATE.md) tracks integration and exact CI for each batch.

| ID | Gate | Priority | Supplied audit | Feature and acceptance criteria | Repository evidence |
|---|---|---|---|---|---|
| F01 | G01 | P0 | V/H | Isolated package and original app retained; verify same-signer increasing-version upgrade on RP6 without losing data. | Same-signer emulator upgrade, byte-identical ARM64 build and disposable signer. Owner/RP6 acceptance pending. [CI 45561309927](https://github.com/alameergym-netizen/Pocket-nexus-2.0/actions/runs/45561309927) [CI 45561309626](https://github.com/alameergym-netizen/Pocket-nexus-2.0/actions/runs/45561309626) [CI 45561310403](https://github.com/alameergym-netizen/Pocket-nexus-2.0/actions/runs/45561310403) |
| F02 | G01 | P0 | V/H | ARM64 deterministic APK rebuild and exact artifact SHA-256; owner-held persistent signing key and backup, no secret in repo. | Same-signer emulator upgrade, byte-identical ARM64 build and disposable signer. Owner/RP6 acceptance pending. [CI 45561309927](https://github.com/alameergym-netizen/Pocket-nexus-2.0/actions/runs/45561309927) [CI 45561309626](https://github.com/alameergym-netizen/Pocket-nexus-2.0/actions/runs/45561309626) [CI 45561310403](https://github.com/alameergym-netizen/Pocket-nexus-2.0/actions/runs/45561310403) |
| F03 | G01 | P0 | V/H | Portable migration of pins, layout, profiles, cloud settings and custom art; physical restart and restore, no uninstall workaround. | Same-signer emulator upgrade, byte-identical ARM64 build and disposable signer. Owner/RP6 acceptance pending. [CI 45561309927](https://github.com/alameergym-netizen/Pocket-nexus-2.0/actions/runs/45561309927) [CI 45561309626](https://github.com/alameergym-netizen/Pocket-nexus-2.0/actions/runs/45561309626) [CI 45561310403](https://github.com/alameergym-netizen/Pocket-nexus-2.0/actions/runs/45561310403) |
| F04 | G01 | P0 | P | Stable versioned preference/data schema and migration rollback/recovery including malformed legacy fields. | Requirement evidence pending. |
| C01 | G02 | P0 | P | Provider registry: exact capabilities per embedded/external PC, retro, native, cloud, console; explicit unsupported/unknown states. | Requirement evidence pending. |
| C02 | G02 | P0 | P | Single active native stream admission/lease, repeat launch and old callback rejection across all entrances. | Retired and queued native connection leases cannot send fixture controller input into the active connection. [CI 45531853656](https://github.com/alameergym-netizen/Pocket-nexus-2.0/actions/runs/45531853656) |
| C03 | G02 | P0 | P | Session lifecycle with observed running/unknown, PiP/background and reconnect; verify #99 or successor. | Android PiP callbacks preserve the active session owner in #98/#99. RP6 background/foreground observation remains pending. [CI 45566251175](https://github.com/alameergym-netizen/Pocket-nexus-2.0/actions/runs/45566251175) |
| C04 | G02 | P0 | V/H | Discovery, wake, pending tasks, shortcuts and async callback ownership; rapid handoff on actual RP6/host. | Async PC launch ownership and profile selection have regression coverage. Rapid hardware handoffs remain pending. [CI 45504103850](https://github.com/alameergym-netizen/Pocket-nexus-2.0/actions/runs/45504103850) [CI 45527922062](https://github.com/alameergym-netizen/Pocket-nexus-2.0/actions/runs/45527922062) |
| C05 | G02 | P0 | V/H | Per-game profile owner across Desktop, reconnect and quality changes; wrong-host/superseded requests cannot apply. | Async PC launch ownership and profile selection have regression coverage. Rapid hardware handoffs remain pending. [CI 45504103850](https://github.com/alameergym-netizen/Pocket-nexus-2.0/actions/runs/45504103850) [CI 45527922062](https://github.com/alameergym-netizen/Pocket-nexus-2.0/actions/runs/45527922062) |
| C06 | G02 | P0 | P | Task/event/resource ownership and deterministic cleanup; cancellation, deadline, process death and race tests. | Requirement evidence pending. |
| S01 | G03 | P0 | P | Original N mark, no stars, black/white pixel and standard variants; coherent tokens, icons, typography and branding. | Requirement evidence pending. |
| S02 | G03 | P0 | P | Home cinematic hero, continue playing, activity and connection status; real emulator screenshot and controller/touch. | Home workspace fixture, startup, PSS and frame timing on Android 13. RP6 measurements pending. [CI 45507580957](https://github.com/alameergym-netizen/Pocket-nexus-2.0/actions/runs/45507580957) |
| S03 | G03 | P0 | P | Cards and artwork variants, metadata and actual provider/install/active badges; cache performance and offline art. | Requirement evidence pending. |
| S04 | G03 | P0 | P | Library responsive structure, filter/search/details/options and stable focus; preserve all prior layouts. | Library wide and compact layouts, details and collection flows. An unplayed PC/Cloud group requires explicit selection. B closes the prompt without dispatch. [CI 45566251253](https://github.com/alameergym-netizen/Pocket-nexus-2.0/actions/runs/45566251253) [CI 45561247427](https://github.com/alameergym-netizen/Pocket-nexus-2.0/actions/runs/45561247427) |
| S05 | G03 | P0 | P | Connect workspace, clear host/client state, onboarding and failure recovery; touch + controller. | Media permission recovery and manual Connect entry on Android 13. [CI 45508944108](https://github.com/alameergym-netizen/Pocket-nexus-2.0/actions/runs/45508944108) |
| S06 | G03 | P1 | P | Media workspace, permission states, Now Playing and accessible controls. | Media permission recovery and manual Connect entry on Android 13. [CI 45508944108](https://github.com/alameergym-netizen/Pocket-nexus-2.0/actions/runs/45508944108) |
| S07 | G03 | P0 | P | System, Settings and Setup navigation, no inaccessible rows, permission recovery, Android escape. | Control/System UI and controller navigation. Physical RP6 mappings pending. [CI 45562997936](https://github.com/alameergym-netizen/Pocket-nexus-2.0/actions/runs/45562997936) |
| S08 | G03 | P0 | P | Control Center/Quick Settings coherent, responsive and correctly separated from stream menu. | Control/System UI and controller navigation. Physical RP6 mappings pending. [CI 45562997936](https://github.com/alameergym-netizen/Pocket-nexus-2.0/actions/runs/45562997936) |
| S09 | G03 | P0 | P | Original Game Details with real Play/choice/settings/source/save/capture actions; focus restore. | Library wide and compact layouts, details and collection flows. An unplayed PC/Cloud group requires explicit selection. B closes the prompt without dispatch. [CI 45566251253](https://github.com/alameergym-netizen/Pocket-nexus-2.0/actions/runs/45566251253) [CI 45561247427](https://github.com/alameergym-netizen/Pocket-nexus-2.0/actions/runs/45561247427) |
| S10 | G03 | P0 | P | Controller-first and touch equivalent actions, compact/wide, large text, contrast, reduced motion and screen reader where practical. | Requirement evidence pending. |
| T01 | G03 | P0 | P | Signature: premium cinematic layout family, actual Home/Library/Control screenshots. | Requirement evidence pending. |
| T02 | G03 | P0 | P | Atmosphere: layered/translucent efficient layout with no-blur fallback and screenshots. | Requirement evidence pending. |
| T03 | G03 | P0 | P | Horizon: streaming-focused layout, host/session/network cards, screenshots. | Requirement evidence pending. |
| T04 | G03 | P0 | P | Pure OLED: true black minimal layout, reduced effects and screenshots. | Integrated #101 with Android 13 Home, Library and Control screenshots and controller/touch checks. Further accessibility and physical verification are open. [CI 45568657536](https://github.com/alameergym-netizen/Pocket-nexus-2.0/actions/runs/45568657536) |
| T05 | G03 | P0 | U | Legacy: retro pixel typography/icons/cards/layout, not palette only; screenshots. | Requirement evidence pending. |
| T06 | G03 | P0 | U | Titanium: telemetry-led technical layout, live/unknown metrics rather than mock numbers; screenshots. | Requirement evidence pending. |
| T07 | G03 | P1 | P | Preserve 15 legacy and 7 Horizon preset inventory (verify prior proposed counts/actual IDs), Cinema/Noir where applicable; no data loss. | Requirement evidence pending. |
| T08 | G03 | P1 | P | Theme Studio preview/apply/cancel/reset, 3 custom slots, safe declarative import/export, per-workspace overrides. | Requirement evidence pending. |
| T09 | G03 | P1 | P | Card geometry, hero, font, icon, focus, accent, background, blur/motion controls and performance fallback. | Requirement evidence pending. |
| T10 | G03 | P1 | P | Sound Studio: 8 packs Glass/Soft/Crisp/Retro/Pop/Aurora/Crystal/Tactile, volume/preview/mute and no gameplay interference. | Requirement evidence pending. |
| W01 | G11 | P1 | P | Widgets 2.0 size/order/visibility/edit, controller/touch and persisted config. | Requirement evidence pending. |
| W02 | G11 | P1 | P | Widgets actual battery/storage/thermal/host/stream/audio, clocks, recent games, favorites, downloads and updates; no fake metrics. | Requirement evidence pending. |
| W03 | G13 | P1 | U | Widget and artwork visibility-driven refresh; suspend unneeded polling while playing; measure overhead. | Requirement evidence pending. |
| I01 | G04 | P0 | P/H | Dedicated Back -> Control outside stream, Stream Control inside; B cancel/gameplay, never Control; RP6 physical scan evidence. | Control/System UI and controller navigation. Physical RP6 mappings pending. [CI 45562997936](https://github.com/alameergym-netizen/Pocket-nexus-2.0/actions/runs/45562997936) |
| I02 | G04 | P0 | P/H | Home -> Quick Resume outside stream, Xbox Guide inside; M1/M2 customizable and migration verified. | Requirement evidence pending. |
| I03 | G04 | P0 | P/H | No app-global radial in stream, distinct stream radial and correct focus/return, no input leakage. | Retired and queued native connection leases cannot send fixture controller input into the active connection. [CI 45531853656](https://github.com/alameergym-netizen/Pocket-nexus-2.0/actions/runs/45531853656) |
| I04 | G04 | P0 | P | Physical calibration, event identity, key-down/up, repeat/chord/long-press and ambiguous B fallback. | Control/System UI and controller navigation. Physical RP6 mappings pending. [CI 45562997936](https://github.com/alameergym-netizen/Pocket-nexus-2.0/actions/runs/45562997936) |
| I05 | G04 | P1 | P | Per-device/per-game remapping, analog dead zones, triggers, input test and hotplug. | Requirement evidence pending. |
| I06 | G04 | P1 | R/H | Haptics, rumble, gyro, DualSense trigger/speaker only when negotiated and supported. | Requirement evidence pending. |
| A01 | G03/G04 | P0 | P | Game Assist 4×3 context action grid, controller/touch and independent stream overlay. | Requirement evidence pending. |
| A02 | G05 | P0 | P | Stream actions: HUD, codec/FPS/bitrate/FG, reconnect, guide, keyboard/mouse, PC, clipboard, mic/audio, screenshot. | Requirement evidence pending. |
| A03 | G04/G07 | P0 | U | Retro Game Assist: save/load/state slots and emulator profile only via supported adapter. | Requirement evidence pending. |
| A04 | G04/G10 | P0 | U | Cloud/native external context actions via supported permission/API; unavailable actions explain why. | Requirement evidence pending. |
| A05 | G03/G05 | P0 | P/H | Overlay open/close doesn't send controls to game, steal B or regress pacing; protected preview fallback. | Requirement evidence pending. |
| A06 | G11 | P1 | U | Assist Music/Messages/Capture/Settings/Switch/Exit with authorized provider-specific actions. | Requirement evidence pending. |
| L01 | G04 | P0 | P | Stable per-provider IDs; duplicate title/edition collision handling, source selection and preferred source; reconcile #98. | Exact PC app IDs and an explicit choice for an unplayed PC/Cloud group are covered. Preferred-source settings remain open. [CI 45566251253](https://github.com/alameergym-netizen/Pocket-nexus-2.0/actions/runs/45566251253) |
| L02 | G04 | P0 | V | Collections add/remove/dedup/filter/restart, verified #94. Re-run integrated regression. | Library collections retain membership, current filter and deletion confirmation in Android 13. [CI 45566251253](https://github.com/alameergym-netizen/Pocket-nexus-2.0/actions/runs/45566251253) |
| L03 | G04 | P1 | P | Favorites, recently played, accurate playtime/history, custom art/metadata and offline caching. | Requirement evidence pending. |
| L04 | G04 | P1 | P | Steam/Epic/GOG/Xbox Windows/Playnite library source and installed/available state; secure actual launch where supported. | Requirement evidence pending. |
| L05 | G04 | P1 | P | Android packages, retro external emulator and supported cores, documented intent mappings. | Requirement evidence pending. |
| L06 | G04 | P1 | P/R | GameNative, GameHub/Lite, Winlator local PC adapter with verified launch identity, no authentication bypass. | Requirement evidence pending. |
| L07 | G04 | P1 | R | ES-DE/RetroArch/Daijisho/Beacon/iiSU reference, platform launch config and artwork; no forced migration. | Requirement evidence pending. |
| L08 | G04 | P1 | P | Per-game quality, controller, audio, performance, preferred source and save profile; correct owner. | Requirement evidence pending. |
| L09 | G04/G07 | P0 | P | Source-aware internal/SD/USB and unavailable root handling; no duplicate or lost identity on detach. | Requirement evidence pending. |
| Q01 | G02/G04 | P0 | P/H | Actual PC Quick Resume matching running game, no unintended quit or double launch. | Requirement evidence pending. |
| Q02 | G04/G07 | P1 | U | Retro Quick Resume via supported compatible save-state or battery save, otherwise explicit reopen. | Requirement evidence pending. |
| Q03 | G04/G10 | P0 | P | External native/cloud resume semantics honest; unknown state vs observed session. | Requirement evidence pending. |
| Q04 | G03/G04 | P1 | P | Nexus Continuity activity cards, history, last source/profile/save availability and safe switching. | Requirement evidence pending. |
| X01 | G10 | P0 | P | xCloud official route and existing library; improve controller-friendly client entry/return, auth state only observed. | Requirement evidence pending. |
| X02 | G10 | P0 | R | Better xCloud Android/web evaluation and supported integration with fallback; no queue/region/auth bypass. | Requirement evidence pending. |
| X03 | G10 | P1 | R | XStreaming and Greenlight evaluation, choose feasible Xbox cloud/home-stream adapter and licensing. | Requirement evidence pending. |
| X04 | G10 | P1 | P/H | GeForce NOW official app/web, provider-specific setup and gamepad; physical account/gameplay later. | Requirement evidence pending. |
| X05 | G10 | P1 | P/H | Boosteroid official app/web, setup and return, no unsupported native promises. | Requirement evidence pending. |
| X06 | G10 | P1 | V/H | Official Xbox Remote Play separate from cloud; actual console/session/controls acceptance later. | Requirement evidence pending. |
| X07 | G10 | P1 | V/H | Official Sony PS Remote Play route; actual console/session acceptance; evaluate Chiaki-ng Android. | Requirement evidence pending. |
| X08 | G10 | P0 | P | All existing custom cloud/PC clients preserved; registry stores mode, capabilities, version and fallback, no fake session. | Requirement evidence pending. |
| X09 | G10 | P1 | P | Connect setup, PC discovery/manual address, favorites, WOL and per-host history/profiles. | Requirement evidence pending. |
| X10 | G10/G05 | P1 | P | LAN/remote/USB/Tailscale route diagnostics; direct/relay only reliably measured, safe private-network guidance. | Requirement evidence pending. |
| R01 | G05 | P0 | I/H | H.264, HEVC, AV1 baseline with codec auto/manual, real 60/120 matrix where supported. | Requirement evidence pending. |
| R02 | G05 | P0 | P | MediaCodec timing/queue bounds, surface recovery and frame-loss accounting; reconcile #96/#97. | Bounded decoder correlation and thermal/Battery Saver observations integrated in #97. Physical stream measurements pending. [CI 45562832346](https://github.com/alameergym-netizen/Pocket-nexus-2.0/actions/runs/45562832346) [CI 45562997936](https://github.com/alameergym-netizen/Pocket-nexus-2.0/actions/runs/45562997936) |
| R03 | G05 | P0 | R | Moonlight X/Starwire Vulkan and host frame timing/direct pacing selective implementation, standard fallback. | Requirement evidence pending. |
| R04 | G05 | P0 | R | Negotiated PyroWave prototype with exact protocol/bitstream/GPU/host capability, no default or false compatibility. | Requirement evidence pending. |
| R05 | G05 | P1 | R | VRR host/client/display negotiation, correct frame pacing, not just refresh rate toggle. | Requirement evidence pending. |
| R06 | G05 | P0 | P | KAST-inspired transient interruption tolerance; preserve game identity, bounded recovery, no duplicate session. | Requirement evidence pending. |
| R07 | G05 | P0 | P | Adaptive bitrate by host/route, jitter/packet loss, hysteresis and recovery; manual preference + safety caps. | Requirement evidence pending. |
| R08 | G05 | P1 | R | FEC adaptation and network roaming, protocol-compatible; fallback and synthetic tests. | Requirement evidence pending. |
| R09 | G05 | P1 | I/H | Frame Generation/LSFG, explicit settings/ownership, no unsupported performance claims. | Requirement evidence pending. |
| R10 | G05 | P1 | R | Optional FSR/SGSR/driver experiments only pinned, licensed and measurable; stock renderer retained. | Requirement evidence pending. |
| R11 | G05/G08 | P1 | P/H | Virtual display, sign-in and host display switching; preserve actual Apollo/Vibepollo compatibility. | Requirement evidence pending. |
| R12 | G05 | P1 | P/H | USB/ADB reverse stable route; USB accessory passthrough separate opt-in capability. | Requirement evidence pending. |
| R13 | G05 | P1 | P | Clipboard existing manual; optional automatic sync with explicit consent, no secret logs. | Requirement evidence pending. |
| R14 | G05/G09 | P0 | P | Encrypted microphone receiver and stream controls; mic permission/mute/teardown and safe fallback. | Requirement evidence pending. |
| R15 | G05 | P0 | P | Six stream profile editor, per-host/game/transport; resolution/FPS/codec/bitrate/HDR/recovery/FG. | Requirement evidence pending. |
| R16 | G05/G13 | P0 | U/H | Repeatable stutter/latency benchmark recorder, p50/p95/p99, drops, route, power/heat, baseline vs candidate. | Requirement evidence pending. |
| R17 | G05 | P0 | H | Real H264/HEVC/AV1/PyroWave 60/120 on LAN/remote/USB where supported; quality, reconnect and thermal evidence. | Requirement evidence pending. |
| H01 | G08 | P0 | P | First-class Windows UI: dashboard/host/library/display/storage/save/audio/security/update/recovery, real screenshots. | Requirement evidence pending. |
| H02 | G08 | P0 | V/H | Pinned TLS, per-device keys/scopes and revocation; physical paired host test. | Requirement evidence pending. |
| H03 | G08 | P0 | V/H | Read-only CPU/RAM/uptime/AC battery + library + separate lock/display-off grants. | Requirement evidence pending. |
| H04 | G08 | P0 | P | Vibepollo metadata adapter -> supported config, apps, encoder, version, session, virtual display and client capabilities. | Requirement evidence pending. |
| H05 | G08 | P1 | R | Adapter vs full Apollo/Vibepollo fork decision based on capability gap, maintainability and upstream license. | Requirement evidence pending. |
| H06 | G08 | P1 | U | RTX 4060 GPU/VRAM/temp/encoder and other host telemetry using supported APIs; real timestamp/unknown. | Windows dashboard runs the installed driver query. CI covers independent counters, unavailable fields and bounded driver capability detection. RTX 4060 load remains pending. [CI 45567866578](https://github.com/alameergym-netizen/Pocket-nexus-2.0/actions/runs/45567866578) |
| H07 | G08 | P1 | P | Scoped secure power/sleep/restart/shutdown, WOL, display/audio controls and explicit authorization/rollback. | Requirement evidence pending. |
| H08 | G08 | P1 | P | Playnite read-only manual export -> supported launch/refresh, installed identity, actual desktop validation. | Requirement evidence pending. |
| H09 | G08/G07 | P1 | P | Secure file transfer and save operations scoped to user-approved roots, no arbitrary commands/path traversal. | Requirement evidence pending. |
| H10 | G08 | P0 | P | Legacy USB-only scope preserved; secure applicable mic/power/library paths before broad remote exposure. | Requirement evidence pending. |
| H11 | G14 | P0 | V/H | ZIP/MSI install, Start menu, upgrade, downgrade refusal, uninstall, settings preservation, exact hashes. | Windows per-user MSI install, upgrade, downgrade refusal and uninstall fixtures verified this payload. Owner installation remains pending. [CI 45567866593](https://github.com/alameergym-netizen/Pocket-nexus-2.0/actions/runs/45567866593) |
| H12 | G14 | P0 | P/H | Windows updater/rollback, production signing owner-local, physical real launch; #97 fixture not production. | Requirement evidence pending. |
| P01 | G06 | P1 | V/H | Android thermal severity/Battery Saver/battery-side power; honest units/unknown and real RP6 validation. | Bounded decoder correlation and thermal/Battery Saver observations integrated in #97. Physical stream measurements pending. [CI 45562832346](https://github.com/alameergym-netizen/Pocket-nexus-2.0/actions/runs/45562832346) [CI 45562997936](https://github.com/alameergym-netizen/Pocket-nexus-2.0/actions/runs/45562997936) |
| P02 | G06 | P1 | R | PServer capability discovery against exact firmware and documented authorization; no mandatory root/Shizuku. | Requirement evidence pending. |
| P03 | G06 | P1 | R | CPU cluster/frequency/governor and allowed tables, read-only then authorized typed writes. | Requirement evidence pending. |
| P04 | G06 | P1 | R | GPU frequency/governor/limits, real supported data/authorization and verification. | Requirement evidence pending. |
| P05 | G06 | P1 | R | Fan/cooling supported modes, thermal protection retained, no unsafe writes. | Requirement evidence pending. |
| P06 | G06/G12 | P0 | P | Typed backend snapshot/journal/readback/rollback/recovery and thermal constraints, fake-backend tests. | Requirement evidence pending. |
| P07 | G06 | P1 | P | Performance Studio profile UI, Eco/Balanced/Performance/Streaming/Emulation/Custom, per-game and explicit apply. | Requirement evidence pending. |
| P08 | G06/G13 | P1 | R | AutoTune based on real measurement, bounded choices, opt-in and reversible; not just bitrate. | Requirement evidence pending. |
| P09 | G06/G13 | P1 | R | CPU/GPU/frame/energy/sustained benchmark tools, record exact metrics and capability. | Requirement evidence pending. |
| P10 | G12 | P1 | P | Battery manager/thermal safety caps preserve manual preference and no fake SoC watt estimate. | Bounded decoder correlation and thermal/Battery Saver observations integrated in #97. Physical stream measurements pending. [CI 45562832346](https://github.com/alameergym-netizen/Pocket-nexus-2.0/actions/runs/45562832346) [CI 45562997936](https://github.com/alameergym-netizen/Pocket-nexus-2.0/actions/runs/45562997936) |
| D01 | G09 | P1 | V/H | Existing stream DSP, route and AudioTrack backlog diagnostics, actual acoustic latency separate. | Requirement evidence pending. |
| D02 | G09 | P1 | P | Multi-band/parametric EQ, safe preamp/limiter/clipping, balance and per-game/per-route audio profiles. | Requirement evidence pending. |
| D03 | G09 | P1 | R | RetroidJamesDSP/RootlessJamesDSP system-wide authorized integration, stream-only fallback. | Requirement evidence pending. |
| D04 | G09 | P1 | P | Audio route/hotplug, USB/Bluetooth/speaker, underrun, buffer and DSP overhead, no fabricated values. | Stream AudioTrack queue diagnostics in #93. Physical audio route, underrun and DSP data remain open. [CI 45533841725](https://github.com/alameergym-netizen/Pocket-nexus-2.0/actions/runs/45533841725) |
| D05 | G09 | P1 | R/H | FreeBuds 7i exact device/firmware capability mapping from LibreBuds; pairing/connection readback. | Requirement evidence pending. |
| D06 | G09 | P1 | R/H | Per-ear/case battery, ANC/awareness/off, gestures/EQ only where supported, unknown fallback. | Requirement evidence pending. |
| D07 | G09 | P1 | P/H | Bluetooth/audio/mic codec/route, permission and background drain; physical FreeBuds measurements. | Requirement evidence pending. |
| D08 | G09/G08 | P1 | P | Discord/game voice mic secure receiver, permission/mute/route/gain, visible capture and stop cleanup. | Requirement evidence pending. |
| V01 | G07 | P1 | P/H | Concurrent internal/SD/USB roots stable identity, detach/reconnect, SAF grant and cache preservation. | Requirement evidence pending. |
| V02 | G07 | P1 | V/H | Verified file copy, hash readback, collision avoidance, source preservation on failure. | Verified copy and Android 13 single-file backup/recovery preserves the current save. [CI 45530960816](https://github.com/alameergym-netizen/Pocket-nexus-2.0/actions/runs/45530960816) [CI 45530960768](https://github.com/alameergym-netizen/Pocket-nexus-2.0/actions/runs/45530960768) |
| V03 | G07 | P1 | P | Move/copy progress/cancel/interruption, free space, staging, safe deletion/confirmation. | Requirement evidence pending. |
| V04 | G07 | P1 | R | CHDroid user-owned image compression, supported file handoff, retain source until verified. | Requirement evidence pending. |
| V05 | G07 | P1 | V/H | Android single-file save copy/restore separated from current save, SHA-256 envelope. | Android 13 verified keep-both restore and compared original/current bytes. RP6 save locations remain in final acceptance. [CI 45568657412](https://github.com/alameergym-netizen/Pocket-nexus-2.0/actions/runs/45568657412) |
| V06 | G07 | P1 | P | Multi-file per-game snapshots/manifests, compatible emulator/state metadata, history/retention. | Requirement evidence pending. |
| V07 | G07 | P1 | P | Restore preview, pre-restore backup, conflict choice/keep both, verified rollback/recovery. | Android v1 save-copy preview explains filename collision and preserves both files. JVM checks reject a valid archive swapped after preview. Multi-file restore, rollback, pre-restore backup and sync remain open. [Android CI](https://github.com/alameergym-netizen/Pocket-nexus-2.0/actions/runs/45568657483) [Files UI](https://github.com/alameergym-netizen/Pocket-nexus-2.0/actions/runs/45568657412) |
| V08 | G07 | P1 | R | RomM versioned authorized read-only catalog, metadata/art, paging/cache and user-owned downloads. | Requirement evidence pending. |
| V09 | G07 | P1 | R | Argosy-inspired per-device save version reconciliation, conflict/queue/idempotence, no timestamp-only overwrite. | Requirement evidence pending. |
| V10 | G07 | P1 | R | Optional pre-launch/post-exit sync where lifecycle and authorized path available; unsupported paths explained. | Requirement evidence pending. |
| V11 | G07/G08 | P1 | V/H | Windows Ludusavi one-game snapshot/preview, hash receipts and previous snapshots retained. | Official Ludusavi v0.31.0 backup fixture checks bytes and digest, and retains previous snapshots. [CI 45561309066](https://github.com/alameergym-netizen/Pocket-nexus-2.0/actions/runs/45561309066) |
| V12 | G07/G08 | P1 | P | Extend multi-game and safe explicit restore, configurable vault, Android-PC sync scope. | Requirement evidence pending. |
| V13 | G07 | P2 | R | Syncthing-Fork documented optional integration, prevent concurrent writes and conflicts, no forced cloud. | Requirement evidence pending. |
| U01 | G11 | P1 | V | Obtainium imported catalog/version and supported GitHub stable tag check. | Imported package version and public GitHub stable tag check. [CI 45533841725](https://github.com/alameergym-netizen/Pocket-nexus-2.0/actions/runs/45533841725) [CI 45533893226](https://github.com/alameergym-netizen/Pocket-nexus-2.0/actions/runs/45533893226) |
| U02 | G11 | P1 | P | Additional source adapters, changelog, ABI/SDK, stable/prerelease and source export. | Requirement evidence pending. |
| U03 | G11 | P0 | P/H | Download/cancel/retry, digest, package/version/signature, explicit Android installer and no silent uninstall. | Requirement evidence pending. |
| U04 | G11 | P1 | P | Self-update vs third-party trust policy, update history, recovery and opt-in checking. | Requirement evidence pending. |
| U05 | G11 | P1 | P | App/Setup Center, installed supported clients, official source links, permission repair and missing-app states. | Requirement evidence pending. |
| M01 | G11 | P1 | P | Local Music playback/library/art/route, authorized external MediaSession controls. | Requirement evidence pending. |
| M02 | G11 | P1 | P | YouTube, YouTube Music, Spotify, Discord official SDK/intent/web where supported; no media/auth bypass. | Requirement evidence pending. |
| M03 | G11 | P1 | P | Nexus Moments screenshot/gallery, game-linked capture metadata, export/share. | Requirement evidence pending. |
| M04 | G11 | P1 | U | Screen recording via supported stream or consented MediaProjection, protected-content fallback, quota/cleanup. | Requirement evidence pending. |
| M05 | G11 | P1 | P | Notification Center authorized filtering, downloads/update/backup/host/battery/heat/storage notifications. | Requirement evidence pending. |
| M06 | G11 | P1 | P | Messages and DND only through allowed APIs, privacy, no automatic reading/sending unrelated messages. | Requirement evidence pending. |
| Z01 | G12 | P1 | P | Smart Scenes typed opt-in rules for game/session/transport/charger/thermal/Bluetooth/SD/manual. | Requirement evidence pending. |
| Z02 | G12 | P0 | U | Conflict priority safety>manual>game>device>default, resource leases and no stale restore of user change. | Requirement evidence pending. |
| Z03 | G12 | P1 | P | Profile/brightness/refresh/audio/DND/backup actions capability-gated, preview and reason/cooldown. | Requirement evidence pending. |
| Z04 | G12 | P0 | U | Journal/reversible actions, crash/reboot recovery, rule conflicts and no unattended privileged/destructive operations. | Requirement evidence pending. |
| Z05 | G12/G13 | P1 | P | Gaming Mode vs Android Mode, suspend nonessential services and reliable exit/restoration. | Requirement evidence pending. |
| O01 | G13 | P0 | P/H | Cold/warm startup, PSS, frame timing, idle/background load; compare baseline and candidate on RP6. | Home workspace fixture, startup, PSS and frame timing on Android 13. RP6 measurements pending. [CI 45507580957](https://github.com/alameergym-netizen/Pocket-nexus-2.0/actions/runs/45507580957) |
| O02 | G13 | P0 | P | Bounded worker/queue/art cache and lifecycle cleanup; long-play regression and no background network flood. | Requirement evidence pending. |
| O03 | G13 | P0 | V/H | One-shot compatibility start, crash/ANR automatic resume pause, recovery/launcher escape; native physical test later. | Android crash/ANR automatic-resume recovery integrated in #91. Native RP6 validation remains pending. [CI 45532820865](https://github.com/alameergym-netizen/Pocket-nexus-2.0/actions/runs/45532820865) |
| O04 | G13 | P1 | P | Setup/Health Check and Diagnostics with device capability, actionable redacted log export and support information. | Requirement evidence pending. |
| O05 | G13 | P0 | H | Sleep/wake, route switch, disconnect, USB/SD removal, decoder surface recreation and thermal/power acceptance. | Requirement evidence pending. |
| E01 | G14 | P0 | P | Source/license/SBOM/provenance, security review and verified exact integrated APK/ZIP/MSI. | Same-signer emulator upgrade, byte-identical ARM64 build and disposable signer. Owner/RP6 acceptance pending. [CI 45561309927](https://github.com/alameergym-netizen/Pocket-nexus-2.0/actions/runs/45561309927) [CI 45561309626](https://github.com/alameergym-netizen/Pocket-nexus-2.0/actions/runs/45561309626) [CI 45561310403](https://github.com/alameergym-netizen/Pocket-nexus-2.0/actions/runs/45561310403) |
| E02 | G14 | P0 | H | Owner signing, data-preserving upgrade and recovery/rollback on actual RP6 and Windows. | Windows per-user MSI lifecycle and disposable signature test. Owner signing and actual-device upgrade remain pending. [CI 45562832365](https://github.com/alameergym-netizen/Pocket-nexus-2.0/actions/runs/45562832365) |
| E03 | G14 | P0 | H | Integrated physical matrix: controls, all routes/codecs, host, storage/saves, earbuds, clients, UI/thermal. | Requirement evidence pending. |
| E04 | G14 | P0 | P | Release notes, version/commit/hash, artifact links and feature evidence; no Stable claim before acceptance. | Requirement evidence pending. |

## Visual acceptance

Use actual screenshots from the tested Android build and Windows application. Keep the seven owner references private. Verify compact and wide layouts, controller focus, touch, large text, reduced motion, empty and failure states, and report measured rendering cost where relevant.

| Reference | Evidence to capture |
|---|---|
| V01 Windows Host and Connect | Windows dashboard and Android Connect with real discovery, pairing, transport, session and scoped authorization states. |
| V02 Theme Studio | Signature, Atmosphere, Horizon, Pure OLED, Legacy and Titanium across Home, Library and Control. Capture distinct layouts, preview, apply and reset. |
| V03 Game Assist | Contextual in-game 4×3 actions for permitted providers, with unavailable capabilities clear and controller input returned to the game. |
| V04 Streaming clients | Actual renderer and session controls, upstream provenance, compatibility and fallbacks. |
| V05 Codecs and VRR | H.264, HEVC, AV1 and negotiated PyroWave at 60/120 FPS where supported. Record route, latency, drops, power, temperature and quality. |
| V06 Save Vault | Game timeline, version and hash, restore preview, existing-save preservation, conflicts and offline sync state. |
| V07 Performance | Safe profile controls and measured CPU, GPU and cooling where supported, with unavailable readings clearly indicated. |
