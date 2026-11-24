# Nexus Host protocol hardening candidate

The existing Windows PowerShell/C# helper is retained, not replaced by a new streaming server.
- Require exact APC1 handshake and complete strict UTF-8 line framing; maximum 1023 payload bytes, optional final CRLF, no embedded CR/NUL, no partial EOF.
- A 15-second maximum whole-line read deadline prevents indefinite slow-drip framing. Bound helper workers to 16 concurrent clients. The USB streaming tunnel and microphone wire protocol are unchanged.
- Windows self-test runs on loopback only, with synthetic Steam fixtures and malformed-handshake checks. New Windows CI compiles and exercises the original helper on a Windows runner.
- Legacy authentication remains unchanged: loopback is trusted, LAN tokens and PINs are plaintext. Do not expose the helper directly to the internet. Use trusted private networks or an encrypted overlay. This is not TLS, authenticated host identity, a full replacement for Apollo/Vibepollo, or a new signed Windows executable.
- The host candidate is independent of the pending Android Buds update. Both must pass integration validation before adoption.

## Integrated client candidate
Windows candidate passed all 32 self-test checks in run 45416055139. This candidate starts from the verified 128-test Buds baseline, retains those Windows changes, and requires strict UTF-8 response frames, explicit 0/1 handshake flags, expected hex token replies and a whole-frame read deadline on Android. Five additional Android protocol tests. Deadlines are per response line, not entire multi-line query; no TLS or server identity proof is added. Integrated Android and Windows CI required.

## Windows desktop dashboard candidate
Nexus Host is a first-class Windows application alongside Android, not a replacement for Vibepollo. This milestone adds a non-modal local WinForms status window launched from the existing tray menu or double-click. It displays companion listener, port, USB bridge/ADB status, PC library initialization, stream-host listener detection and autostart; manual refresh and explicit PIN copy. No PIN in dashboard summary, but the legacy tray and status storage remain unchanged. No new network endpoints or Windows privileges. Six synthetic USB status checks extend the existing loopback self-test. Windows CI must pass, then manually verify window open/close, refresh, DPI scaling, keyboard navigation, PIN copy and existing tray/stream behavior on the real Windows machine. A listening port does not prove Vibepollo interoperability.

Remaining Windows release gates: secure authenticated transport and host identity, revocable device pairing, credential storage, allowlisted actions, Vibepollo capabilities/version negotiation, Playnite/library adapters, telemetry, save vault and verified file transfer, installer/uninstaller and signed upgrade, recovery, and integrated RP6/Windows testing. Keep all existing USB, mic, power and library paths working. The dashboard is not yet the final desktop design.
