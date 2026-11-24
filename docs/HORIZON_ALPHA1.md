# Horizon Alpha 1 — device validation

## Verified CI baseline
- Tested commit: `7b993a854b05af740d862ab52702300ee0e4efe5`
- [Successful CI run](https://github.com/alameergym-netizen/Pocket-nexus-2.0/actions/runs/45407246029)
- 100 unit tests passed, zero failures or skips in the recorded job.
- [Development APK archive](https://github.com/alameergym-netizen/Pocket-nexus-2.0/actions/runs/45407246029/artifacts/14926242691) (temporary GitHub Actions artifact; sign-in may be required).
- ARM64 development application ID: `com.pocketnexus.console.debug`. Keep the original application installed. This is not a signed production release; do not rely on debug builds sharing signing keys across CI runs.
- Do not import or publish personal backups in this repository.

## Retroid Pocket 6 test checklist
Record firmware version and PASS / FAIL / NOT TESTED for each item. Avoid sending serial numbers, IP addresses, pairing secrets or account information in screenshots and logs.

| Area | Procedure | Expected |
|---|---|---|
| Install | Extract the archive and install the APK alongside 1.0 | Both apps remain available |
| Launch | Launch Horizon, navigate Home and Settings with controller and touch | No crash, usable focus |
| Migration | Import a locally saved v1 settings backup after first launch | Portable settings restored; pairing and device-bound permissions re-established separately |
| Theme | Select Signature, Horizon and Pure OLED | Home renders and input remains responsive |
| Search | Search for a cached PC game and a ROM by name | Matching launch entries appear |
| Streaming | Pair with existing Apollo/Vibepollo host; begin with conservative quality; then try 1080p120 manually | Stream starts and exits cleanly; verify actual decoded FPS, latency and controller response separately |
| Connection | Run Connection Check and PC helper diagnostics | Reported measurements identified as estimates; no false direct/relay claims |
| Storage | Open Storage Volumes with SD and optional SSD attached; reopen after disconnect | Available volumes and ROM root state reflect Android visibility |
| Input | Try Back/Guide tap, double and hold; open Control Center | No trapped controls; existing stream mapping preserved |
| Audio | Check wired/Bluetooth outputs and supported media controls | No automatic EQ or ANC change |
| Obtainium | Import a local export | Catalog metadata visible; no APK installation |
| Retroid Bridge | Inspect firmware, CPU/GPU and thermal rows | Available readings or explicit unavailable state; no privileged writes |

If a crash occurs, record the exact steps and sanitized stack trace. A successful CI build does not establish physical-device compatibility, performance gains, DSP support or PServer capability.

## Not yet implemented
PServer clock/fan writes and rollback, automatic hardware tuning, native JamesDSP processing, FreeBuds ANC/EQ control, encrypted PC helper replacement, full console redesign, signed release and live Obtainium updates.
