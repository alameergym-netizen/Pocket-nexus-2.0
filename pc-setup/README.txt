POCKET NEXUS - PC TOOLS
=======================

Double-click  PocketNexus-PC.bat  and leave the window open (you can minimise it).
It can start with Windows (it asks once). It replaces "Artemis Pocket PC": close the old
window first (its Windows startup shortcut is swapped for the new one by itself).

What it does
  - USB-C streaming: plug the Retroid in with USB debugging on. Downloads ADB by itself.
  - PC power from the handheld: sleep, hibernate, restart, shut down, lock, sign out,
    turn the display off (in-stream menu > PC power, or the PC card's options).
  - PC game library: the handheld lists the games installed on this PC (Steam, Epic, GOG),
    starts them for you (through Apollo's Desktop when Apollo has no entry for them) and
    asks Steam to install games you own. Steam's install window is confirmed by itself
    when Windows lets it take the focus; otherwise confirm it once on the PC.
  - Microphone: the handheld's mic on this PC. Install VB-Audio Virtual Cable (free,
    https://vb-audio.com/Cable/) and pick "CABLE Output" as the microphone in your game
    or chat app. Turn it on in the in-stream menu > Microphone to PC.

Over Wi-Fi: stream once over the cable and the handheld links itself, or use the PC card's
options > "PC helper PIN" and type the PIN shown in this window.

Wi-Fi port: Pocket Nexus PC listens on port 47110 over Wi-Fi. To use another one (that port is
busy, or your network needs another), right-click its icon next to the clock > "Wi-Fi port",
or run  PocketNexus-PC.bat -WifiPort 47120 . On the handheld, set the same port in the PC's
options > "Pocket Nexus PC Wi-Fi port" (or stream once over the USB cable: it picks it up).

Legacy Wi-Fi control can be disabled from the Nexus Host tray. Restart Nexus Host after your
stream or transfer finishes. Legacy power, library and mic commands will then be accepted
only over loopback, including the existing USB bridge. It does not change a firewall rule
or the ports for streaming or encrypted status. Existing installations keep their Wi-Fi setting.

Secure host status (development)
  - Keep NexusSecureHost.cs beside PocketNexus-PC.ps1. Open the Windows tray menu and choose
    Secure status > Enable TLS status listener. This starts TCP 47111 on the PC.
  - Use Copy device pairing code and give the handheld a name. Transfer the code privately
    and paste it into the handheld's PC card options > Secure host status. Clear the clipboard
    after pairing. A code includes a credential and authenticates the PC you copied it from.
  - Revoke device in the Windows tray if a handheld or its key is lost. Replacing the key for
    one device leaves other devices paired. The handheld's Forget action clears its local copy.
  - Allow TCP 47111 only on the Windows private network and from the handheld or trusted subnet
    if the Windows firewall blocks it. Setup does not open this port automatically.
  - To also view the PC game list, choose Copy status and game list pairing code from the
    tray and pair that code on the handheld. Each new scope is assigned per device.
  - The encrypted listener carries read-only status and game metadata. Power, launching, mic, USB,
    and stream paths use their existing connections. Keep the plaintext control port private.

Playnite game list (development)
  - Nexus Host dashboard > Playnite Library shows the manual export, with name filtering,
    Refresh, Open details and Start in Playnite for entries installed at export time.
  - Each action rechecks the complete export and sends only its selected game GUID through
    Playnite's documented URI handler. A changed export must be refreshed first.
    A sent request does not prove the game started; check Playnite for progress or errors.
  - Refresh reads the snapshot, not the live Playnite database. Export again after installing
    or removing games. This local window adds no handheld or network launch permissions.
  - Copy the playnite folder from this archive into Playnite's Extensions folder and restart
    Playnite. The installed configuration usually uses %APPDATA%\Playnite\Extensions.
  - Open Playnite's main menu > Extensions > Pocket Nexus > Export visible games to Nexus Host.
    Confirm the export, then refresh the secure game list on the handheld. Repeat after changes.
  - Use Remove Nexus Host export in Playnite to stop providing those entries to paired devices.

Vibepollo status (development)
  - In the Vibepollo web UI, create a token scoped to GET /api/metadata. In the Windows Nexus
    Host dashboard, open Vibepollo status and select the local management port (usually 47990).
  - Browse to the public certificate from the Vibepollo installation. Its default config path
    is credentials/cacert.pem. Paste the scoped token, then select Verify and save.
  - Refresh saved shows cached encoder readiness reported by the host and virtual display driver
    information when provided. Forget clears the local setup. Revoke the token in Vibepollo if
    it is no longer needed or if it was exposed.
  - The adapter connects to loopback HTTPS. No Windows firewall change or Android secret is needed.

If you used the old "ArtemisPocket-USB-Bridge" window: close it, this one replaces it.

Handhelds that DO have USB tethering:
  -> other-handhelds\ArtemisPocket-USB-Setup.bat  (run once)
