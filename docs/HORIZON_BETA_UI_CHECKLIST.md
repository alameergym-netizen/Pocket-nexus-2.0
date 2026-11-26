# Beta device checklist: Horizon UI, Library, Studio, Clients, accessibility

This list covers the Claude lane for the integrated debug beta, and supplements `HORIZON_FINAL_PHYSICAL_ACCEPTANCE.md`. Nothing here has been run on a device. Each item is for the owner to mark pass, fail or not tested against the exact beta SHA. Record photos, screen recordings or `uiautomator` dumps as evidence. CI emulator runs (Android 13, x86_64) are not device evidence.

Controller mappings that must stay unchanged:
- Back opens Nexus Control.
- B cancels.
- Home is the Guide in a stream.
- M1 and M2 keep their user-set mappings.

## Library (S03, #137, #139, #154)
- [ ] A ROM whose emulator isn't installed shows the amber **Needs app** chip. Its Game Options say which app it needs, and TalkBack reads it as the card's state.
- [ ] **Favorites:** the Favorite row in Game Options toggles ♥ on the card, the Favorites filter lists the game, and the favourite survives a restart.
- [ ] **Running badge:** with Home open, start a PC stream, then open the Library within 30 s. That PC game's card shows **● Running**, and TalkBack reads "Running on <PC>". End the stream and stay in the Library: the chip clears within about 35 s.
- [ ] D-pad: L1/R1 change filters, Y opens Game Options, A plays, B goes back. Focus is never lost after a filter change.

## Clients (U05, #150)
- [ ] With a PSX ROM and no RetroArch, Settings → Clients shows "Your Library is waiting for RetroArch (1)". It appears beside the title in landscape and in place of the intro on short portrait screens.
- [ ] The RetroArch row reads "Not installed · 1 Library game waits for it", and its details offer the official page. After RetroArch is installed and the app returns to the screen, the row and the line update.

## Theme Studio (T08, T09, #143, #146, #156)
- [ ] Preview, Apply, B-cancel, Default and "Look before the last apply" behave as labelled, and closing the Studio without Apply changes nothing.
- [ ] An imported look with a dark accent is refused with the "unreadable" message.
- [ ] **Typeface:** Settings → Look → Typeface (Inter, System, Condensed) changes Home text after returning to Home. Nothing overflows at 130% font scale.
- [ ] **Workspace accents:** set Library accent to Ice. The Library's focus, section bar and rings turn ice, while Home, Settings and Nexus Control keep the look's accent. Applying the Default look clears the override.

## Accessibility and motion (#141 and earlier)
- [ ] Android "Remove animations" on: Home, Library and Studio crossfades stop, and the Look tab's Animations row explains why.
- [ ] TalkBack: every row in Library, Clients and Studio announces its name and state. Headings are announced as headings.
- [ ] Display size Largest plus font 130%: Clients, Studio and Library keep their actions reachable without clipped buttons.

## Brand (#140)
- [ ] The Home brand mark is crisp at RP6 density, and the pixel variant is used where the brand is shown small.
