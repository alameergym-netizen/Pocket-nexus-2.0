# Nexus Host protocol hardening candidate

The existing Windows PowerShell/C# helper is retained, not replaced by a new streaming server.
- Require exact APC1 handshake and complete strict UTF-8 line framing; maximum 1023 payload bytes, optional final CRLF, no embedded CR/NUL, no partial EOF.
- A 15-second maximum whole-line read deadline prevents indefinite slow-drip framing. Bound helper workers to 16 concurrent clients. The USB streaming tunnel and microphone wire protocol are unchanged.
- Windows self-test runs on loopback only, with synthetic Steam fixtures and malformed-handshake checks. New Windows CI compiles and exercises the original helper on a Windows runner.
- Legacy authentication remains unchanged: loopback is trusted, LAN tokens and PINs are plaintext. Do not expose the helper directly to the internet. Use trusted private networks or an encrypted overlay. This is not TLS, authenticated host identity, a full replacement for Apollo/Vibepollo, or a new signed Windows executable.
- The host candidate is independent of the pending Android Buds update. Both must pass integration validation before adoption.
