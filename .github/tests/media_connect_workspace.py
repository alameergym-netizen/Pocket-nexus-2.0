"""Exercise the Media permission recovery and the Connect entry point on Android 13."""
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from threading import Thread
import re
import time
import subprocess
import sys

import control_workspace as controls
from home_workspace import bounds, resource

controls.RESULTS = Path("media-connect-ui-results")
AUDIO = "android.permission.READ_MEDIA_AUDIO"
ACCESS = "Music access settings"


def node_with_label(tree, label):
    return next((n for n in tree.iter("node") if label in (n.get("text"), n.get("content-desc"))), None)


def tap(node):
    left, top, right, bottom = bounds(node)
    assert left < right and top < bottom
    controls.adb("shell", "input", "tap", str((left + right) // 2), str((top + bottom) // 2))


def scroll_to(tree, label):
    for _ in range(22):
        scrolls = [n for n in tree.iter("node") if n.get("class") == "android.widget.ScrollView"]
        assert scrolls, "No scrollable panel for " + label
        scroll = scrolls[-1]
        left, top, right, bottom = bounds(scroll)
        node = node_with_label(scroll, label)
        if node is not None:
            _, y1, _, y2 = bounds(node)
            if y2 - y1 >= 12 and top < (y1 + y2) // 2 < bottom:
                return node
        x = str((left + right) // 2)
        step = (bottom - top) // 2
        controls.adb("shell", "input", "swipe", x, str(bottom - 20), x, str(bottom - 20 - step), "440")
        time.sleep(0.35)
        tree = controls.snap()
    raise AssertionError("Could not reach " + label)


class ServerInfo(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path.split("?", 1)[0] != "/serverinfo":
            self.send_error(404)
            return
        xml = (f'<root status_code="200"><hostname>Nexus Codec Fixture</hostname>'
               f'<uniqueid>32d90d49-b296-4941-b480-e91428c29297</uniqueid><appversion>7.1.431.0</appversion>'
               f'<GfeVersion>3.23.0.0</GfeVersion><PairStatus>0</PairStatus><state>SUNSHINE_SERVER_FREE</state>'
               f'<HttpsPort>47984</HttpsPort><ExternalPort>{self.server.server_port}</ExternalPort>'
               f'<ServerCodecModeSupport>{0x07800000 | 0x10000 | 0x100 | 1}</ServerCodecModeSupport></root>').encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/xml")
        self.send_header("Content-Length", str(len(xml)))
        self.end_headers()
        self.wfile.write(xml)


def verify_host_offers():
    with ThreadingHTTPServer(("127.0.0.1", 0), ServerInfo) as server:
        worker = Thread(target=server.serve_forever, daemon=True)
        worker.start()
        port = "tcp:" + str(server.server_port)
        controls.adb("reverse", port, port)
        try:
            entry = controls.wait_for("host address field", lambda t: resource(t, "hostTextView") is not None)
            tap(resource(entry, "hostTextView"))
            controls.adb("shell", "input", "text", "127.0.0.1:" + str(server.server_port))
            controls.key("ENTER")
            tree = controls.wait_for("advertising host in Connect", lambda t:
                                     resource(t, "pocketPcCarousel") is not None and controls.has(t, "Nexus Codec Fixture"))
            carousel = resource(tree, "pocketPcCarousel")
            card = next(n for n in carousel.iter("node") if n.get("long-clickable") == "true")
            left, top, right, bottom = bounds(card)
            x, y = str((left + right) // 2), str((top + bottom) // 2)
            controls.adb("shell", "input", "swipe", x, y, x, y, "850")
            controls.capture("pc-options-after-hold")
            try:
                caught = controls.adb("shell", "run-as", controls.PKG, "cat", "files/pocket_last_crash.txt")
            except subprocess.CalledProcessError:
                caught = b""
            if caught:
                (controls.RESULTS / "pc-options-caught.log").write_bytes(caught)
                raise AssertionError("PC options reported a caught exception: " + caught.decode(errors="replace")[:1400])
            options = controls.wait_for("PC options", lambda t: any(
                n.get("class") == "android.widget.ScrollView" and controls.has(n, "Connection test")
                for n in t.iter("node")) or controls.has(t, "View Details"))
            tap(scroll_to(options, "View Details"))
            controls.wait_for("current host codec advertisement", lambda t: any(
                "Host-advertised video codecs: H.264, HEVC, AV1, PyroWave (no Nexus decoder)" in n.get("text", "")
                for n in t.iter("node")))
            controls.capture("06-pc-codec-details")
            print("PASS: a manually discovered host exposes its codec offers in PC Details", flush=True)
        finally:
            server.shutdown()
            worker.join()
            controls.adb("reverse", "--remove", port)


def verify_display_modes():
    controls.setup_device(320)
    controls.key("BUTTON_START")
    settings = controls.wait_for("Settings tabs", lambda t: controls.has(t, "System") and controls.has(t, "Settings"))
    controls.tap_label(settings, "System")
    system = controls.wait_for("System recovery settings", lambda t: controls.has(t, "Compatibility stream start"))
    tap(scroll_to(system, "This handheld"))
    panel = controls.wait_for("handheld information", lambda t: controls.has(t, "Chip") or controls.has(t, "Active mode"))
    scroll_to(panel, "Active mode")
    active = controls.snap()
    assert any(re.search(r"[0-9]+ × [0-9]+ · [0-9.]+ Hz", n.get("text", "")) for n in active.iter("node"))
    scroll_to(active, "Adaptive refresh support")
    modes = controls.snap()
    assert controls.has(modes, "Not reported"), "Android 13 does not expose the adaptive refresh API"
    controls.capture("07-handheld-display")
    controls.key("BUTTON_B")
    controls.wait_for("B returns to System", lambda t: controls.has(t, "System") and not controls.has(t, "Active mode"))
    print("PASS: System Settings opens actual Android display capabilities and returns with B", flush=True)


def main(apk):
    controls.RESULTS.mkdir(exist_ok=True)
    controls.adb("install", "-g", apk, timeout=180)
    controls.adb("shell", "svc", "power", "stayon", "true")
    controls.adb("shell", "settings", "put", "system", "screen_off_timeout", "1800000")
    controls.setup_device(320)
    controls.adb("shell", "am", "force-stop", controls.PKG)
    controls.adb("shell", "pm", "revoke", controls.PKG, AUDIO)
    controls.adb("shell", "pm", "set-permission-flags", controls.PKG, AUDIO, "user-set", "user-fixed")

    seed_dir = "/sdcard/Android/data/" + controls.PKG + "/files/preview"
    controls.adb("shell", "mkdir", "-p", seed_dir)
    seed = controls.RESULTS / "seed.txt"
    seed.write_text(
        "pref\tbool\tpocket_onboarding_pending\tfalse\n"
        "pref\tbool\tpocket_first_run_done_v1\ttrue\n"
        "pref\tbool\tpocket_animations\tfalse\n"
        "pref\tint\tpocket_music_tab2\t1\n"
        "pref\tstring\tpocket_home_layout\thorizon_workspace\n",
        encoding="utf-8",
    )
    controls.adb("push", str(seed), seed_dir + "/seed.txt")
    controls.launch()
    home = controls.wait_for("Music in Home quick access", lambda t: controls.has(t, "Music"))
    tap(node_with_label(home, "Music"))
    controls.wait_for("Music permission recovery accepts controller focus",
                      lambda t: controls.focused(t, ACCESS))
    controls.capture("01-music-permission-denied")
    controls.key("BUTTON_A")
    controls.wait_for("Android app details for Music access", lambda t:
                      any(n.get("package") == "com.android.settings" for n in t.iter("node")))
    controls.adb("shell", "pm", "clear-permission-flags", controls.PKG, AUDIO, "user-set", "user-fixed")
    controls.adb("shell", "pm", "grant", controls.PKG, AUDIO)
    controls.adb("shell", "input", "keyevent", "KEYCODE_BACK")
    controls.wait_for("Music refreshes after a grant", lambda t: not controls.has(t, ACCESS)
                      and any("No music here yet" in n.get("text", "") for n in t.iter("node")))
    controls.capture("02-music-library")
    controls.key("BUTTON_B")
    controls.wait_for("B returns from Media to Home", lambda t: resource(t, "homeRow") is not None)
    controls.key("BUTTON_R1")
    connect = controls.wait_for("Connect shows the no-host state and setup controls",
                                lambda t: controls.has(t, "Connect to PC")
                                and resource(t, "horizonConnectAdd") is not None
                                and resource(t, "horizonConnectHelp") is not None
                                and resource(t, "horizonConnectRemote") is not None)
    controls.capture("03-connect-empty")
    tap(resource(connect, "horizonConnectRemote"))
    controls.wait_for("Remote Play lists the two official routes", lambda t:
                      controls.has(t, "Xbox Remote Play") and controls.has(t, "PS Remote Play"))
    controls.capture("04-connect-remote-play")
    controls.key("BUTTON_B")
    connect = controls.wait_for("B closes Remote Play and leaves Connect in place", lambda t:
                                not controls.has(t, "Xbox Remote Play")
                                and resource(t, "horizonConnectAdd") is not None)
    tap(resource(connect, "horizonConnectAdd"))
    controls.wait_for("manual host entry", lambda t: resource(t, "hostTextView") is not None)
    controls.capture("05-connect-manual-entry")
    print("PASS: Media permission recovery and Connect controller-to-touch navigation", flush=True)
    verify_host_offers()
    verify_display_modes()


if __name__ == "__main__":
    try:
        main(sys.argv[1])
    finally:
        controls.RESULTS.mkdir(exist_ok=True)
        for name, args in (
                ("final.png", ("exec-out", "screencap", "-p")),
                ("crash.log", ("shell", "logcat", "-b", "crash", "-d"))):
            try:
                (controls.RESULTS / name).write_bytes(controls.adb(*args, timeout=8))
            except subprocess.SubprocessError:
                pass
