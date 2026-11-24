# Final integrated physical acceptance (deferred by owner)

No interim device tests requested. This checklist is cumulative and not evidence of passing. Preserve the installed app and local backups; no uninstall to bypass signing. Test final integrated Android and Windows builds together, record exact versions and public signer digest. Never send keys, passwords, tokens, PINs, raw private backups or personal game paths.

- G01: establish private persistent signing key locally, secure offline backup; export portable settings and compare imported pins, profiles, layout, cloud preference; re-pair and restore permissions as needed; verify same-signer increasing-version in-place upgrade and restart. Keep saves and ROMs independently backed up.
- G02/G04: single active session ownership, repeated launch, alternate game selection, external launch semantics, suspend/return; B normal back, dedicated Back controls, Home Xbox Guide in PC stream, M1/M2 unchanged; verify no app-wide radial/Quick Switch while streaming. Button Test only if ambiguity remains.
- G03: original Home/Library/Connect/Media/System layouts and controller/touch, empty states, contrast, scaling/reduced motion; actual screenshots.
- G05: Vibepollo LAN and remote/direct/Tailscale and USB; FPS, dropped frames, network and decode latency, H264/HEVC/AV1 as supported, reconnect, framegen on/off; user-reported stutter remains unresolved until measured.
- G06/G07: hardware capability and safe rollback, internal/SD/USB simultaneously, disconnect and reattach, transfer verification, save conflict; no unsafe thermal bypass.
- G08: Windows helper connection independently of Vibepollo (prior recording showed stream working and library helper unavailable), trusted pairing/revoke, supported commands, library and installer, no unintended firewall changes.
- G09/G10: FreeBuds audio/mic/routes/DSP and cloud/remote clients; sign-in via trusted clients, no credential sharing.
- G11/G12/G13: update/permissions/capture/notifications/automation with explicit approval, cold/warm start/frame/power measurements, sleep/wake/recovery/Android escape.
- G14: integrated regression, signed update, security and license review. Record pass/fail/not tested and evidence for each item. Do not mark untested gates complete.
