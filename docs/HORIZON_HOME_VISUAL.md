# Horizon home visual pass 1

Native UI changes inspired by the approved Horizon/Aeronis dashboard: accent-colored active section capsule, always-labeled Quick Access glass chips, horizontal scrolling dock with explicit D-pad movement (RTL-aware), retained customizable entries and tools, separate bottom hints, responsive hero width. No fabricated game counts or art. Existing home layouts, saved pins, artwork, actions, and dock preference remain intact. Portal/grid/list still hide the dock as before. This is the first visible pass, not a pixel-identical recreation. Android CI and actual 16:9 RP6 layout/focus verification required; narrow/translated labels may ellipsize.

## Integrated visual pass 2
Integrated over the verified console button profile. Adds responsive header wordmark, accent-tinted Play and Home focus rings, labeled Details where space allows, Featured Game label only when height permits, and real pinned/played-pin counts on larger shelf layouts. Three visual-policy tests. This is native code, not a rendered preview or final pixel-identical design. Screen-size and localization behavior must be verified on RP6.

## Hero transition validation
Verify an uncached logo, an entry without artwork and the Add tile while moving rapidly with D-pad. The correct selected title must remain readable until its logo is ready; stale motion and shade must not leak onto entries without art. Verify with animations both enabled and disabled and on the RP6 in each supported layout. This is polish on the current renderer, not the final Horizon shell redesign.
