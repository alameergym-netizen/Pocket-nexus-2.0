# Nexus Headphones Lab — owner hardware test

Separate application id: com.pocketnexus.headphones.lab.v2. Does not replace Nexus, access its data, request Internet, scan, pair, or run a background service. Android 12+; intended for RP6 Android 13. Compiles the exact four production headphone Java files and the existing synthetic unit tests. Debug-signed experimental APK; do not distribute as a production release.

1. Pair FreeBuds 7i normally and close other earbud companion apps.
2. Install the APK; open Nexus Headphones Lab from the Android launcher.
3. Choose paired device, grant Nearby devices, select exact earbuds (suffix disambiguates). Readings start automatically; Refresh now is available. If model unconfirmed or timeout, stop and report status; no writes are enabled.
4. Compare left/right/case and mode with AI Life or physical observation. Screenshot the Lab result (no need to send device identifiers).
5. If identity and current state returned, test one mode at a time. Confirm explicit change; check actual sound and returned state. Automatic readback refresh enables the next write; no manual refresh needed on success. Try Off, Awareness, Adaptive, Light, Balanced, Deep. If a write times out, read again because device state might have changed.
6. Test with ordinary audio playing, then with a separate Nexus stream only if the basic probe works. Stop if audio drops or the earbuds disconnect. No hardware EQ/firmware/codec changes in this build.

This is a physical feasibility test, not the final integrated Control Center UI. No root/LSPosed required. An unrecognized model or unsupported transport is a valid test outcome.

## v0.2
Separate package to avoid debug-signature upgrade conflict with v0.1. Close v0.1 before opening v0.2. Foreground-only automatic reads every 15 seconds and after a command; stop on failed read, cancellation or leaving while active. No automatic writes. Compare the native voice prompt by selecting Awareness, then ANC (earbud-selected level). The latter uses the documented 0xFF mode-transition vector, not a verified prompt fix; no voice language change or fabricated audio is performed. Report whether the earbuds audibly announce the change, and whether audio is interrupted. Native firmware behavior is not established by ACK alone.
