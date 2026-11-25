# Nexus Horizon: the streaming layout family (T03)

Nexus Horizon is the preset with accent `0xFFA8C8E8`, the Art backdrop, frost, Crystal sounds, the Portal layout and ring-zoom focus. On top of the shared engine it now adds three streaming cards (**Host**, **Session**, **Network**) on every surface the look covers:

| Surface | Where the cards sit | Arrangement |
|---|---|---|
| Home | Right of the hero, level with its buttons, below the games row | Side by side when each card gets 140 dp, otherwise three one-line rows. Hidden below 180 dp of room. |
| Library | A row under the title, above the tabs and the games | One line each, side by side. Only on screens at least 420 dp tall (in the app's own dp, after its UI scale), so short screens keep their room for games. |
| Nexus Control | Under the title and clock, above the tiles | One line each. Side by side when they fit across the panel, otherwise stacked. |

## What each card claims

The rules are in `HorizonStreamCards`, which is plain Java with unit tests in `HorizonStreamCardsTest`.

- **Host.** Shows one PC: a PC reporting a running game first, then a PC that is online, then the PC played last. **Online** and **Offline** come only from Home's PC poll and expire after 30 s. After that the card shows "Seen …" with the time, or "Not checked yet". It names the route (local network, remote address, manual address) only when the active address matches one of the PC's known addresses. With no PC paired it says so and points to Streaming.
- **Session.** **Running** only while an online PC reports a game id less than 30 s ago. The game's name comes from that PC's cached app list, otherwise the card says "A game is running". **No stream running** appears only after every PC has answered. Until then the card says "Waiting for your PCs". The game played last is shown as history, never as running.
- **Network.** Android's transport (Wi-Fi with band, signal level and link rate; wired; mobile data; VPN). The link rate is labelled as the radio's link, not as measured throughput. Unknown numbers are left out. Nothing is measured or sent.

The Library and Nexus Control don't poll PCs. They show Home's last observation under the same 30 s rule.

## Actions

- Host opens Streaming.
- Session resumes the running game on Home, through the same `launchOnPc` path a Home PC tile uses. Elsewhere, and when nothing is running, it opens Streaming.
- Network opens Android's internet panel (Wi-Fi settings before Android 10).

All three respond to touch and to the controller's A button.

## Controller

- **Home.** RIGHT from Options reaches the Host card. Moving along the cards stops at the last one. LEFT, or UP when the cards are stacked, goes back to Options. UP from the row of cards returns to the games row.
- **Library.** UP from the games reaches the cards, and DOWN returns to the selected game.
- **Control.** UP from the tiles reaches the cards, and DOWN returns to the first tile. Control's "first key focuses the first tile" rule now leaves focus on the cards when they already have it.

## Evidence

`.github/tests/stream_family_workspace.py`, run by `horizon-stream-family-ui.yml`, checks two configurations on an Android 13 emulator:

- **960×540 dp at 100 % text:** Home cards side by side, Library cards above the games, Control cards above the tiles.
- **640×360 dp at 130 % text:** Home cards stacked, no Library cards (short screen), Control cards side by side.

In both it checks:

- The card values are honest: no PC is paired on the emulator, so the cards must say "No PC paired" and "No stream running", never online, offline or resume.
- Geometry: no overlap with the hero, the games row or the tiles; cards at least 40 dp tall.
- Controller navigation into and out of the cards.
- A single tap on Network opens Android's panel, and BACK returns to Home.

It saves screenshots of each surface.

Physical RP6 checks, and real paired-PC states (online, running, resume), are part of the final integrated acceptance.
