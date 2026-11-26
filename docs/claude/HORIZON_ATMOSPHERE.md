# Nexus Atmosphere: the layered layout family (T02)

Nexus Atmosphere is the preset with accent `0xFFE8EBF0`, the Art backdrop, frost, Glass sounds, the Wide shelf and zoom focus. It used to share the Wide shelf with the Glass TV and Cinema presets. It now arranges its surfaces as layers over the art:

| Surface | Layers |
|---|---|
| Home | The art. A glass **tray** under the label, the row and the dock, inset 12 dp from the sides and clear of the hints. The hero on its own glass **veil**, lifted 12 dp off the tray. The veil only appears when the hero has at least 200 dp of room. Below that the hero keeps its plain layout and only the tray remains. |
| Library | The details on a glass pane. The games on a separate glass tray (`horizon_atmosphere_tray`). |
| Nexus Control | A lighter pane (`horizon_atmosphere_panel`) over the window blur, with a light from above and a bright 1 dp rim. |

## Blur and the fallback

The rules are in `HorizonAtmosphere`, which is plain Java with tests in `HorizonAtmosphereTest`.

- **Real blur** needs Android 12+ and the user's live-glass setting on. It uses the existing `LiveGlass` path: one quarter-size render node of the backdrop, shared by every pane.
- **Without blur** (Android 11 or older, live glass off, or a screen with no recorded backdrop), the same layers are drawn as plain translucent glass by the same `GlassDrawable`. There is no extra pass and no bitmap work.
- Nexus Control's pane is see-through (alpha about 0xB8–0xC8) only when the window blur is behind it. Without the blur it is nearly solid (0xF0 or more), so its words never sit on raw art.

## Evidence

`.github/tests/atmosphere_workspace.py`, run by `horizon-atmosphere-ui.yml` on an Android 13 emulator, checks three passes:

- 960×540 dp with live blur;
- 640×360 dp at 130 % text with live blur;
- 960×540 dp with live glass turned off (the fallback).

In each pass it checks:

- On Home, the tray spans the screen and reaches above the games. The hero stands clear of the tray and, where it has room, sits inside its veil (content inset at least 12 dp, lifted at least 8 dp).
- In the Library, the games sit inside their tray, as a pane separate from the details.
- Nexus Control shows its Atmosphere pane, and controller focus moves from Sleep to Wi-Fi.

It saves screenshots of every surface in every pass. Physical RP6 checks remain part of the final integrated acceptance.
