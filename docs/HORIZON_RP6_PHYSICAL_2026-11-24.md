# Retroid Pocket 6 physical observation — 2026-11-24

Evidence: user-supplied 143-second screen recording of Pocket Nexus 2.0 (Dev). Reviewed sampled frames every four seconds; no private video, identifiers, IPs or account data committed. Exact build SHA, install history, firmware and hardware input event source cannot be established from this recording alone. The old narrow Control panel is visible, so this is not evidence of #35's redesigned workspace.

| Time (approx.) | Observed | Assessment |
|---|---|---|
| 00:00–00:12 | Add to Home > Android app, Core Keeper pinned | Pass: existing app selection and Home pin |
| 00:12–00:32 | Core Keeper cover, logo, art and metadata; multiple Home layouts | Pass: visible artwork/metadata and layout switch |
| 00:36–00:44 | PC discovered, not paired; radial menu; Retro asks for file access | Discovery and radial rendering observed; streaming and ROM launch not tested |
| 00:48–01:12 | Settings, Look and Controls sections; theme and sound level; Streaming and Android sections | Navigation observed; settings persistence across upgrades not tested |
| 01:20–01:36 | Old Nexus Control opens; Bluetooth settings open and Bluetooth state later changes to ON | Control and Bluetooth handoff observed |
| 01:36–02:04 | Volume display switches between 0% and 100%; brightness 69% then 62%; system-write permission prompt | Volume UI changes; actual audio output not verifiable; brightness permission incomplete |
| 02:08–02:23 | Accessibility prompt, Retro file-access prompt, radial menu, discovered PC unpaired | Permissions not completed, global control and actual PC stream unverified |

Device panel displayed 120 Hz, battery and temperature; PULSE recognized as performance tool. This does not establish rendered FPS, GPU tuning, PServer write capability, host latency, secure transport, or actual game execution. No crash observed in sampled frames; no stability benchmark. No evidence of v1 backup import, same-signer physical upgrade, persistent release signer or signed release. G01 remains open. Physical smoke testing now has partial evidence for the existing Home, controls and discovery. #35 independently passed emulator UI navigation/screenshots; test that new APK separately. Do not uninstall old installation if signing mismatch. Keep user backups local.

Next physical acceptance: identify exact APK; preserve original install; export local settings, verify same signer and higher version before in-place upgrade, check settings/pins after upgrade and restart; grant brightness/accessibility/file permissions only as needed; pair on trusted LAN and confirm live stream, Back and Xbox Guide. Never send credentials, pairing PIN, private signing key, or private backup to repository.
