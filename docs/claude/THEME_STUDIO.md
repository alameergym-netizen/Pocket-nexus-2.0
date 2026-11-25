# Theme Studio

Settings › Look › Theme Studio previews any look on Home, Library and Control before it changes anything. Acceptance rows: T07, T08 (except per-workspace overrides), part of T09 and S10.

## Behaviour
- **Preview:** moving the focus to a look previews it. A tap previews too. L1/R1 or the scene tabs switch the preview between Home, Library and Control. Previewing never writes preferences.
- **Apply:** A on a look moves to **Apply look**; A again (or a tap) applies it. Applying stores the replaced look as `horizon_look_undo`, which appears under Tools as "Look before the last apply".
- **Cancel:** B or **Close without applying** leaves without writing anything.
- **Reset:** "Nexus default" previews the look of a fresh install. Applying it is the reset.
- **Custom looks:** three slots, stored in the original version-1 JSON (`horizon_look_slot_0..2`), so looks saved by earlier builds still read. Clearing a slot needs a second press.
- **Scope:** applying changes appearance and interface sounds only: accent, backdrop, material, sound pack, Home layout, focus style, card density, card corners, art motion, colour art and live glass. Streaming, controls, library data, volume and interface size are untouched.
- **Own layouts:** Pure OLED and Titanium are previewed with their own Home, Library and Control layouts, as the real screens are.

## Text format (copy and paste)
```
pocket-nexus-look 1
accent #F2F2F2
backdrop art
material glass
sound crisp
layout tiles
focus ring_zoom
density 0
corners 0
motion on
color-art on
live-glass on
```
The header must come first, and every key must appear exactly once. Values are restricted to these sets:

| Key | Allowed values |
|---|---|
| `accent` | `#RRGGBB` |
| `backdrop` | `glow`, `ambient`, `art`, `black` |
| `material` | `frost`, `glass` |
| `sound` | one of the eight sound packs |
| `layout` | a Home layout id |
| `focus` | `ring_zoom`, `ring`, `zoom` |
| `density`, `corners` | `0`, `1`, `2` |
| `motion`, `color-art`, `live-glass` | `on`, `off` |

Text over 2 KB, unknown or repeated keys, and unsupported values are rejected without changing anything.

## Accessibility
- Rows and buttons are at least 44–48 dp, with text drawn in near-black or white depending on the accent's contrast.
- Section names are headings, the status line is a polite live region, and the preview carries a spoken description of what it shows.
- On short screens with large text, the picture shrinks so Apply and Close stay visible.
- Opened by touch, the studio keeps touch mode, so Settings stays on its tab on return. Opened with a button, the applied look has the focus.

## Not yet covered
- per-workspace overrides
- hero, font and icon controls
- distinct Signature, Atmosphere, Horizon and Legacy layouts (T01–T03, T05)
- physical appearance on the Retroid Pocket 6, which is checked at final integrated acceptance
