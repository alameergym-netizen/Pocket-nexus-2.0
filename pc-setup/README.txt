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
  - The encrypted listener carries read-only host status. Existing power, library, mic, USB,
    and stream paths use their existing connections. Keep the plaintext control port private.

If you used the old "ArtemisPocket-USB-Bridge" window: close it, this one replaces it.

Handhelds that DO have USB tethering:
  -> other-handhelds\ArtemisPocket-USB-Setup.bat  (run once)
