# Nexus Legacy layout (T05)

Nexus Legacy is the 8-bit look among the seven Horizon presets. Until this change it was a palette, sound and grid preset only. It now has its own Home, Library and Control treatment; choosing any other look restores the original type and surfaces.

## What is different
- **Typography.** The Home details, Library header and details, and every word on the Nexus Control panel use the pixel typeface Pixelify Sans. Titles are set in capitals. Arabic and other scripts fall back to the system font, as with Inter.
- **Home.** The layout follows the Pure OLED and Titanium pattern:
  - Quick Access along the bottom and a square-card cover grid on the left.
  - A **dialog box** on the right: a dark panel, a double gold rule and corners stepped one pixel in, with a "Select a game" line over the title.
  - No game-colour glow, and the summary card is hidden.
- **Cards.** Corners are square, and focus is a square gold ring with no zoom.
- **Library.** A warm-black background, the dialog-box detail panel with a "Game index" line, square framed cards and no zoom on focus.
- **Control.** No blur behind, a dialog-box panel, square tiles and no lift or zoom on focus.
- **Scanlines.** Faint horizontal scanlines (10% black, one line in three) are drawn over Home and Library. Every rule is snapped to whole device pixels so lines stay crisp.

## Font provenance
| Item | Value |
|---|---|
| Font | Pixelify Sans, variable `wght` 400–700, by Stefie Justprince |
| Licence | SIL Open Font License 1.1, shipped as `assets/licenses/OFL-PixelifySans.txt` |
| Source | `google/fonts` at `6ebe737e44fd64ee736bbc6feed6136a6dbfd222`, `ofl/pixelifysans/PixelifySans[wght].ttf` |
| Upstream | `eifetx/Pixelify-Sans` at `39df74aba80df8157546034b878e8be1eb565ced` (per `METADATA.pb`) |
| SHA-256 (`res/font/horizon_pixelify_sans.ttf`) | `9ba86cd010a4de309d263ceff8e8044092c9db7efda869620cb9ff1c4389e8a5` |
| SHA-256 (`OFL.txt`) | `b66ba46f511a851ab09998b5a5a9fdbb102545a3864cb993095e1745996873a7` |

## Evidence and limits
The `Horizon Legacy theme UI` workflow seeds the Legacy preset with three games. It checks Home (grid left of the dialog panel, Quick Access), Library (split detail and grid with its index line) and Control (dialog panel, controller focus moving from Sleep to Wi-Fi). It runs at 960×540 dp and at 640×360 dp with 130% text, and captures screenshots of each.

Still open:
- pixel-styled icons
- a Legacy variant in the Theme Studio preview, added after #115 is integrated
- physical RP6 appearance
