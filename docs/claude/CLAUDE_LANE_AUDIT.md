# Claude lane audit — Shell, Theme Studio, Library presentation, client and service surfaces

Baseline: `horizon-foundation` at `77c2eced15892a84ab478a6c693fc4b69ce90b16` (#106), Foundation CI [45625858824](https://github.com/alameergym-netizen/Pocket-nexus-2.0/actions/runs/45625858824) passed. This is a source audit for the proposed Claude lane (G03, G04 presentation, G10 client UX, G11 services). It records code paths and evidence gaps; it does not mark any criterion as accepted. The shared [feature ledger](../FEATURE_ACCEPTANCE.md) remains with the integration owner.

## Path ownership at this baseline

Open Codex drafts (treated as reserved): #112 stream equalizer, #113 FreeBuds pairing, #114 benchmark frame evidence. They touch `Game.java`, `MediaCodecDecoderRenderer`, `HorizonStreamBenchmark`, `AndroidAudioRenderer`, `HorizonStreamDsp`, `HorizonAudio`, `HorizonPermissions`, `StreamMenuView`, `ConsoleActivity`, the stream and system tabs of `QuickSettingsActivity`, `AndroidManifest.xml` permissions and `.github/tests/control_workspace.py`. Claude batches avoid these hunks. #16 and #19 are stale November 24 drafts behind the current base.

Claude Theme Studio batch (`claude/horizon-theme-studio`) uses new `pocket/studio/**`, `ui/HorizonLook.java`, `ui/HorizonThemeStudio.java`, `res/values/horizon_theme_studio.xml`, a one-line change to the Look tab row in `QuickSettingsActivity`, one new `<activity>` element in the manifest, and its own test and workflow files.

## Criteria map (Claude lane)

| Row | Priority | Existing implementation | Evidence at baseline | Gap |
|---|---|---|---|---|
| S01 | P0 | Pocket/Nexus wordmark assets, `PocketFonts`, `PocketIcons` | None mapped | Original N mark token set and icon audit |
| S02 | P0 | `HomeActivity`, `HorizonHomeWorkspace`, `HeroArt`, `WidgetShelf` | Home workspace emulator timing | Hero/continue/activity states across themes |
| S03 | P0 | `CardTag`, `KindBadge`, `PlaceholderArt`, `RemoteImages` | None mapped | Badge truth (installed/active) and offline art cache evidence |
| S04/S09 | P0 | `LibraryActivity`, `HorizonLibraryLayout`, `GamePageActivity` | Library UI CI | Game details per provider, focus restore after actions |
| S05/S06 | P0/P1 | `PcView` Connect workspace, `MusicActivity` | Media/Connect UI CI | Onboarding and failure states |
| S07/S08 | P0 | `QuickSettingsActivity`, `ControlCenterActivity`, `HorizonControlLayout` | Control UI CI | Inaccessible-row sweep, Settings navigation |
| S10 | P0 | Per-screen focus handling, UI scale setting | None mapped | Font scale, reduced motion, TalkBack labels and contrast across screens |
| T01–T03 | P0 | Presets 15–17 in `PocketTheme` reuse existing layouts | None mapped | Distinct Signature/Atmosphere/Horizon layouts and screenshots |
| T04 | P0 | Pure OLED branches in Home, Library, Control | #101 screenshots | Accessibility and physical checks |
| T05 | P0 | Legacy is a palette, sound and grid preset only | None | Pixel typography/cards/layout (not palette only) |
| T06 | P0 | Titanium telemetry workspaces (#106) | Titanium emulator workflow | Ledger row not yet updated by integration owner |
| T07 | P1 | 15 classic + 7 Horizon presets | `PocketThemeTest` count | Inventory order guard (added in Theme Studio batch) |
| T08 | P1 | Three JSON slots in a console panel (save/apply/delete) | None | Preview, cancel, reset, undo, import/export (Theme Studio batch); per-workspace overrides open |
| T09 | P1 | Density, corners, art motion, focus, accent, material settings | `HorizonAppearanceTest` | Hero, font and icon controls; performance fallback evidence |
| T10 | P1 | Eight sound packs in `PocketSounds` | None | Sound Studio preview/mute and gameplay isolation |
| W01–W03 | P1 | `WidgetShelf`, `WidgetCards` | None | Edit/order/visibility, real data only, refresh suspension |
| L01–L09 | P0/P1 | `UniversalLibrary`, `GameShelf`, collections, ROM sources | Library UI CI for L01/L02 | Presentation of preferred source, favourites/history, per-game settings surface |
| X01–X10 | P0/P1 | `CloudClients`, `CloudService`, `BetterXcloud`, `HorizonRemotePlay`, `HorizonCloudDispatch` | Cloud client CI for X02 | Registry display of mode/capability/version/fallback for every client (X08) |
| U03–U05 | P0/P1 | `ObtainiumCatalog`, `Updater`, `HorizonUpdatePolicy` | U01 CI | App/Setup Center with installed/missing state and official links |
| M01–M06 | P1 | `MusicActivity`, `PocketNotifications`, `StreamScreenshot` | None mapped | Permission-aware controller flows |

## Order of work

1. Theme Studio 2.0 (T07, T08, part of T09 and S10): this batch.
2. Client registry and Setup Center surfaces (X08, U05, Q03 honesty), read-only and capability-labelled.
3. Accessibility sweep on Settings, Library and Control (S10), then distinct Legacy and Signature/Atmosphere/Horizon layouts (T01–T03, T05).

Physical RP6, Vibepollo and owner signing stay in the final integrated matrix.
