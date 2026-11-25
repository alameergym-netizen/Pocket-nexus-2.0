# Client, upstream and design reference register
The expanded specification supplied these upstream and design references on 2026-11-25. Pin the exact source and audit licensing, dependencies, and binary provenance before adoption. Candidate and unresolved identities need verification. Links are reference material. Owner design images are held privately.

## 1. PC streaming clients and hosts — highest priority
| Project | Platform/role | Concrete investigation | Source |
|---|---|---|---|
| Moonlight Android | Android baseline | MediaCodec, input, host compatibility, codecs and fallbacks | https://github.com/moonlight-stream/moonlight-android |
| moonlight-common-c | Shared protocol | session negotiation, native lease, RTP/FEC, input; compare exact vendored submodule | https://github.com/moonlight-stream/moonlight-common-c |
| Artemis / Moonlight Noir | inherited Android fork lineage | preserve existing virtual display, input, per-app settings and existing extensions; verify actual upstream history in repo | https://github.com/ClassicOldSong/moonlight-android (candidate upstream; verify against history) |
| Artemide | Artemis-derived reference | touchpad, settings and frame-pacing behavior; identify canonical repo from Starwire attribution | https://github.com/SicarioGaming/Starwire |
| Starwire | Android client | Vulkan, direct pacing, PyroWave, clipboard, encrypted mic/receiver, haptics/USB; evaluate feature-by-feature and binary provenance | https://github.com/SicarioGaming/Starwire ; https://github.com/SicarioGaming/Starwire/blob/main/windows/README.md |
| Moonlight X | Android client | direct frame timing, Vulkan renderer, PyroWave, per-app settings, quick launch | https://github.com/MoreOrLessSoftware/moonlight-android |
| KAST | Android Artemis fork | tolerate transient network loss rather than immediately dropping session; compare recovery state machine | https://github.com/MikalaiKryvusha/KAST |
| Aurora Android | Android Moonlight fork | GPU Vulkan PyroWave decoding; documented Solarflare host requirement, do not assume Vibepollo interoperability | https://github.com/Koloses/aurora-android |
| Vibemis (Android) | Rust protocol architecture candidate | original Android Rust core, GLES/upscaling and controller features; verify canonical repo is accessible, distinguish similarly named Linux clients | https://github.com/ineedmypills/Vibemis (identity surfaced via mirror; verify primary) |
| Vibemis (Linux/SteamOS) | separate project | controller UX and Vibepollo workflows, not an Android engine | https://github.com/0xFFFFail/vibemis ; https://github.com/navyas321/vibemis (verify canonical) |
| Ariane | earlier named client | exact repository unresolved; recover from prior blueprint or upstream links, do not substitute a similarly named app | unresolved |
| Moonlight X Turnip / Moonlight V+ / Moonlit Artemis | research candidates | inspect Starwire credits, compare driver/haptics/render behavior; no unverified driver binaries | https://github.com/SicarioGaming/Starwire/releases |
| Nonary VRR Moonlight fork | desktop compatibility reference | host/client VRR and bitstream capability negotiation; follow official Vibepollo protocol document to client | https://github.com/Nonary/Vibepollo/blob/master/docs/pyrowave-protocol.md |
| Vibepollo | primary Windows host | metadata, scoped API, virtual display, encoder, FEC, PyroWave, VRR; prefer adapter if sufficient | https://github.com/Nonary/Vibepollo ; https://github.com/Nonary/Vibepollo/blob/2.18.9/docs/api.md ; https://github.com/Nonary/Vibepollo/blob/master/docs/pyrowave-protocol.md |
| Apollo | Windows host | backward compatibility, virtual displays, current protocol extensions | https://github.com/ClassicOldSong/Apollo |
| Sunshine | Windows/Linux host | standard host compatibility and fallback | https://github.com/LizardByte/Sunshine |
| Solarflare | optional host research | Aurora PyroWave reference, not a requirement to replace primary host | https://github.com/Koloses/Solarflare |
| PyroWave streaming reference | ecosystem overview | verify actual protocol revisions and decoder dependencies, no assumed cross-host compatibility | https://github.com/joemossjr16/pyrowave-streaming |
| Penumbra | optional new reference | Android handheld-specific efficiency, only after baseline priorities | https://github.com/arcanite24/penumbra |
| Nightjar | optional new reference | unified host/library and cross-platform UX; assess selectively | https://github.com/RamazanKara/nightjar |

Key research findings: Starwire documents a separate encrypted microphone receiver and credits Moonlight X for Vulkan/PyroWave. KAST describes temporary-network-loss resilience. Aurora says its PyroWave route requires Solarflare. Vibepollo's protocol notes point to a compatible VRR Moonlight fork. These are upstream claims, not Nexus feature evidence. Compare exact protocol revisions, GPU capability and licensing before porting. A full Rust rewrite or host fork is not required unless evidence justifies the cost.

## 2. Cloud, consoles and media clients
| Client/service | Intended integration | Source |
|---|---|---|
| Xbox Cloud Gaming | official browser/client and existing xCloud provider; gamepad-first entry, region/account/queue restrictions remain | https://www.xbox.com/play |
| Xbox Remote Play | separate owner-console route, official authentication | https://www.xbox.com/en-US/consoles/remote-play |
| Better xCloud | evaluate Android wrapper/web enhancement and supported options; do not bypass service restrictions | https://github.com/redphx/better-xcloud ; https://github.com/redphx/better-xcloud-android ; https://better-xcloud.github.io/ |
| XStreaming | compare open-source mobile Xbox cloud/home-stream client; inspect protocol/auth terms | https://github.com/Geocld/XStreaming |
| Greenlight | Xbox client design/protocol reference; not an Android drop-in | https://github.com/unknownskl/greenlight (verify canonical) |
| GeForce NOW | official Android/web client, safe return and setup; no invented native API | https://www.nvidia.com/en-us/geforce-now/ |
| Boosteroid | official app/web route; gamepad, supported region, login stays with provider | https://boosteroid.com/ |
| PS Remote Play | official Sony app/store fallback; owner console/account | https://www.playstation.com/en-us/support/games/playstation-remote-play-on-mobile-devices/ |
| Chiaki-ng / Chiaki | PS4/PS5 open-source client reference; confirm Android build status, licensing and authorized auth | https://github.com/streetpea/chiaki-ng |
| YouTube / YouTube Music | official supported external app/web and media controls where permitted | https://www.youtube.com/ ; https://music.youtube.com/ |
| Spotify | official app/SDK/MediaSession, respect subscription and DRM | https://developer.spotify.com/ |
| Discord | official client/handoff, authorized voice path through PC; never collect passwords/private messages | https://discord.com/developers/docs/intro |

No claims that an official service supports embedded third-party auth, catalog scraping, arbitrary overlay controls, or universal game resume. Keep old cloud URLs/configuration and user-owned credentials local. Native integrations only when allowed and technically feasible; external handoff remains a valid explicit capability.

## 3. Local PC gaming, emulation and game metadata
| Project | Use | Source |
|---|---|---|
| GameNative | local PC-game provider; inspect stable IDs, documented launch path, library and save capabilities | https://github.com/utkarshdalal/GameNative ; https://gamenative.app/compatibility |
| GameHub / GameHub Lite | existing user-installed provider integration; community modification is not upstream SDK; no redistribution or DRM bypass | https://github.com/Producdevity/gamehub-lite |
| Winlator | local Windows application compatibility launcher; documented external intents/shortcuts only | https://github.com/brunodev85/winlator |
| ES-DE | Android emulator launch/ROM layout and provider compatibility reference | https://es-de.org/ ; https://github.com/RetroDECK/ES-DE/blob/retrodeck-main/ANDROID.md (downstream reference; verify official docs) |
| RetroArch / libretro | supported cores, launch and save-state capabilities; preserve external emulator routes | https://github.com/libretro/RetroArch ; https://docs.libretro.com/ |
| Daijishō / Beacon / iiSU | controller-first launcher, layout and artwork inspiration; no copying proprietary assets | identify official sites/repositories before reuse |
| Recompa | earlier reference name; verify identity before proposing integration | unresolved |
| CHDroid | user-owned disc compression workflow, verify before deleting source | https://github.com/Ottavio97/CHDroid |
| Playnite | Windows SDK/export/launch integration; manual snapshot is currently implemented | https://github.com/JosefNemec/Playnite ; https://api.playnite.link/ |
| Steam/Epic/GOG/Xbox Windows | authorized local game inventory/launch through existing providers and Playnite, no account bypass | use official platform documentation and installed client interfaces |
| SteamGridDB | artwork metadata for user-owned libraries, authorized API usage | https://www.steamgriddb.com/api/v2 |

## 4. Hardware, performance, audio and earbuds
| Project | Use | Source |
|---|---|---|
| PULSE | no-root PServer capability reference for RP6, typed frequency/thermal control; validate actual firmware | https://github.com/keiretrogaming/pulse |
| ClusterTune | per-cluster and GPU limit/profile reference, no-root PServer path where supported | https://github.com/AurelioB/ClusterTune |
| Calibrate-SoC | pre-alpha monitoring/tuning/benchmark reference, lists RP6; investigate safe lifecycle and rollback | https://github.com/mayusi/Calibrate-SoC |
| LibreBuds | FreeBuds 7i is listed upstream; inspect supported per-model operations, Bluetooth permissions and readback | https://github.com/librebuds |
| RetroidJamesDSP | Retroid-specific system audio candidate, explicit compatibility | https://github.com/Andiweli/RetroidJamesDSP |
| RootlessJamesDSP | non-root system audio candidate, capture/permission limitations; stream-only fallback | https://github.com/timschneeb/RootlessJamesDSP |
| Android thermal APIs | thermal status, power-state support | https://developer.android.com/reference/android/os/PowerManager |
| Android frame pacing | Swappy/Choreographer, OpenGL/Vulkan presentation | https://developer.android.com/games/sdk/frame-pacing/ |
| Android MediaProjection | consent and protected capture limitations | https://developer.android.com/media/grow/media-projection |
| Android SAF | storage grants/scoped access | https://developer.android.com/guide/topics/providers/document-provider |
| Android game controller | input, axis and button handling | https://developer.android.com/develop/ui/views/touch-and-input/game-controllers |
| Android audio capture | playback capture restrictions and allowed usage | https://developer.android.com/media/platform/av-capture |
| NVIDIA NVML | Windows GPU/VRAM/temperature/encoder support where available | https://developer.nvidia.com/management-library-nvml |

PULSE and Calibrate-SoC report RP6 support in upstream docs; Nexus's read-only bridge is not yet verified hardware tuning. A vendor service must be used only with legitimate device authorization and firmware-supported limits. LibreBuds's list is not proof of every 7i feature on every firmware. Do not confuse Android thermal status with exact temperatures, or power estimate with SoC watts.

## 5. Storage, saves, updates and networking
| Project | Use | Source |
|---|---|---|
| RomM | self-hosted game catalog, explicit version/capabilities and user-owned content | https://github.com/rommapp/romm ; https://github.com/rommapp/romm/blob/5.4.1/backend/utils/openapi.py |
| Argosy Launcher | gamepad-first RomM integration, save synchronization, per-device conflicts and supported state actions | https://github.com/rommapp/argosy-launcher ; https://github.com/rommapp/argosy-launcher/wiki/Save-Sync ; https://github.com/rommapp/argosy-launcher/wiki/Save-States ; https://github.com/rommapp/argosy-launcher/wiki/RomM-Integration |
| Ludusavi | existing Windows backup/restore preview, extend safe snapshots and conflict workflows | https://github.com/mtkennerly/ludusavi ; https://github.com/mtkennerly/ludusavi/blob/v0.31.0/docs/cli.md |
| Syncthing-Fork | optional user-controlled file synchronization; verify current canonical Android fork | https://github.com/researchxxl/syncthing-android (candidate; verify) |
| Obtainium | safe source adapters and update flow; existing imported catalog only partial | https://github.com/ImranR98/Obtainium |
| Tailscale | official direct/relay diagnostics and user-managed VPN setup | https://tailscale.com/kb/1257/connection-types |
| GitHub release metadata | stable assets/digest/version | https://docs.github.com/en/rest/releases/assets |
| Android package install | explicit user approval and signer continuity | https://developer.android.com/reference/android/content/pm/PackageInstaller |
| WiX | Windows installer build and lifecycle | https://wixtoolset.org/ |

## 6. Visual design references (inspiration, not assets to copy)
- The seven private owner references establish the direction for original Nexus layouts. Use PlayStation 5 for cinematic hierarchy, PlayStation Portal for stream-first navigation, SteamOS for library and system hierarchy, and Apple TV for motion and focus. iiSU, Beacon, ES-DE, Playnite and Aeronis are additional design references, subject to identity verification.
- Official product/design pages: https://www.playstation.com/en-us/ps5/ ; https://www.playstation.com/en-us/accessories/playstation-portal-remote-player/ ; https://www.steamdeck.com/ ; https://developer.apple.com/design/human-interface-guidelines/tvos ; https://es-de.org/ ; https://playnite.link/ .
- Theme and layout directions are visual references, not proof of current implementation. Use original Nexus branding, typography and icons; do not embed third-party screenshots or game artwork into distributed APK without permission.

## 7. Repository evidence links
- Baseline #95: https://github.com/alameergym-netizen/Pocket-nexus-2.0/pull/95 ; https://github.com/alameergym-netizen/Pocket-nexus-2.0/commit/ccfa603aa34e8720d59905f4e3a48910bdcd8831
- Development state: https://github.com/alameergym-netizen/Pocket-nexus-2.0/blob/horizon-foundation/docs/DEVELOPMENT_STATE.md
- Blueprint: https://github.com/alameergym-netizen/Pocket-nexus-2.0/blob/horizon-foundation/docs/HORIZON_SYSTEM_BLUEPRINT.md
- Host: https://github.com/alameergym-netizen/Pocket-nexus-2.0/blob/horizon-foundation/docs/HORIZON_HOST.md
- Physical acceptance: https://github.com/alameergym-netizen/Pocket-nexus-2.0/blob/horizon-foundation/docs/HORIZON_FINAL_PHYSICAL_ACCEPTANCE.md
- Integration checkpoints and exact test runs are listed in [DEVELOPMENT_STATE.md](DEVELOPMENT_STATE.md). Track row-level evidence in [FEATURE_ACCEPTANCE.md](FEATURE_ACCEPTANCE.md).

## Existing pins in this repository

| Source | Version and usage | Evidence |
|---|---|---|
| moonlight-common-c | `c999436858471dfefa7617af3b7dc03ec1644ce4`, vendored submodule in the integration baseline. | [Submodule registration](../.gitmodules) |
| Vibepollo | 2.18.9 metadata API used by the Windows companion adapter. | [Host implementation and licensing](HORIZON_HOST.md) |
| Ludusavi | v0.31.0 official Windows binary verified in an isolated CI fixture. User supplies the executable. | [Windows CI](https://github.com/alameergym-netizen/Pocket-nexus-2.0/actions/runs/45561309066) |
| Obtainium | v1.7.5 used as the reference for the catalog import. | [Source](https://github.com/ImranR98/Obtainium/tree/v1.7.5) |
| Better xCloud Android | The official a0.24.3 APK matched SHA-256 `107e9b14b014ef8a0b9cbb79ac1aac24f6c610ef168b47d6447df93e85e14ec4`. Its manifest declares `com.redphx.betterxc`, Android API 26 or later, and an exported launcher. Nexus offers the installed app as an opt-in client and retains its existing web, browser and XStreaming choices. If a selected title has no supported HTTPS handler, Nexus opens the client without recording that title as opened. Account and gameplay acceptance remain open. | [Official APK release](https://github.com/redphx/better-xcloud-android/releases/tag/a0.24.3) · [MIT license](https://github.com/redphx/better-xcloud-android/blob/f88d184fd7c71a3f98567bcda3fe48fae26be06f/LICENSE) |
