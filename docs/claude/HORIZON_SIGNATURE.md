# Nexus Signature: the cinematic layout family (T01)

Nexus Signature is the preset with accent `0xFFF2F2F2`, the Art backdrop, glass, Crisp sounds, the Tiles shelf and ring-zoom focus. It used to share the Tiles shelf with the Console preset. Now each screen is framed like a film:

| Surface | Frame |
|---|---|
| Home | A black bar under the status line and tabs, and a black bar holding the games and the dock like a credits roll. The game's art shows in the band between, with soft 18 dp edges. A short accent rule marks the title, and the hero stands inside the picture. The bars appear only when the band would be at least 150 dp tall. On shorter screens the picture is kept and only the rule remains. |
| Library | Letterbox bars behind the title and the hints. On wide screens the game's art becomes a widescreen still (2.39:1, 72–162 dp tall). |
| Nexus Control | A near-black slate with 6 dp corners and a hairline in the accent, in the same letterboxed frame. |

The rules are in `HorizonSignature`, which is plain Java with tests in `HorizonSignatureTest`. `HorizonSignatureFrame` draws the bars as two views behind the content, so they add no drawing pass over it.

## Evidence

`.github/tests/signature_workspace.py`, run by `horizon-signature-ui.yml` on an Android 13 emulator, checks two sizes.

**960×540 dp:**
- Both bars on Home, with the games on the bottom bar and the hero inside the band.
- At least 100 dp of art between the bars, and the accent rule inside the hero.
- The Library is framed, and its still has a widescreen aspect (2.2–2.6).
- Control shows the slate, and controller focus moves from Sleep to Wi-Fi.

**640×360 dp at 130 % text:**
- No bars on Home (the picture is kept), with the rule still present.
- The Library is framed without a still.
- Control shows the slate.

It saves screenshots of every surface. Physical RP6 checks remain part of the final integrated acceptance.
