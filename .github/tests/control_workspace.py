"""Exercise existing Back mapping and controller navigation on a clean CI emulator."""
from pathlib import Path
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

PKG = "com.pocketnexus.console.debug"
RESULTS = Path("control-ui-results")

def adb(*args, timeout=25):
    return subprocess.check_output(["adb", *args], timeout=timeout, stderr=subprocess.STDOUT)

def snap():
    adb("shell", "uiautomator", "dump", "/sdcard/control-ui.xml")
    xml = adb("exec-out", "cat", "/sdcard/control-ui.xml")
    (RESULTS / "last-screen.xml").write_bytes(xml)
    return ET.fromstring(xml)

def has(tree, label):
    return any(label.casefold() in (n.get("text", "").casefold(), n.get("content-desc", "").casefold())
               for n in tree.iter("node"))

def focused(tree, label):
    return any(n.get("focused") == "true" and has(n, label) for n in tree.iter("node"))

def wait_for(label, predicate):
    deadline = time.monotonic() + 65
    while time.monotonic() < deadline:
        try:
            tree = snap()
            if predicate(tree):
                return tree
        except (ET.ParseError, subprocess.SubprocessError):
            pass
        time.sleep(0.6)
    raise AssertionError("Did not reach " + label)

def key(code):
    adb("shell", "input", "dpad", "keyevent", "KEYCODE_" + code)

def capture(label):
    (RESULTS / (label + ".png")).write_bytes(adb("exec-out", "screencap", "-p"))
    print("Captured " + label, flush=True)

def launch():
    adb("shell", "am", "start", "-W", "-a", "android.intent.action.MAIN",
        "-c", "android.intent.category.LAUNCHER", "-n", PKG + "/com.limelight.PcView")

def control(tree):
    return has(tree, "Nexus Control") and has(tree, "QUICK ACTIONS")

def tap_label(tree, label):
    node = next(n for n in tree.iter("node") if label in (n.get("text"), n.get("content-desc")))
    left, top, right, bottom = map(int, re.findall(r"\d+", node.get("bounds", "")))
    assert left < right and top < bottom
    adb("shell", "input", "tap", str((left + right) // 2), str((top + bottom) // 2))


def open_control():
    key("F8")
    return wait_for("Control workspace via explicit test Back mapping", control)

def setup_device(density):
    adb("shell", "am", "force-stop", PKG)
    adb("shell", "wm", "size", "1080x1920")
    adb("shell", "wm", "density", str(density))
    adb("shell", "input", "keyevent", "KEYCODE_WAKEUP")
    adb("shell", "wm", "dismiss-keyguard")
    launch()
    time.sleep(3)

def main(apk):
    RESULTS.mkdir(exist_ok=True)
    adb("install", "-g", apk, timeout=180)
    adb("shell", "svc", "power", "stayon", "true")
    adb("shell", "settings", "put", "system", "screen_off_timeout", "1800000")
    setup_device(320)
    adb("shell", "am", "force-stop", PKG)
    seed_dir = "/sdcard/Android/data/" + PKG + "/files/preview"
    adb("shell", "mkdir", "-p", seed_dir)
    seed = RESULTS / "seed.txt"
    seed.write_text("pref\tbool\tpocket_onboarding_pending\tfalse\n"
                    "pref\tbool\tpocket_first_run_done_v1\ttrue\n"
                    "pref\tbool\tpocket_gb_enabled\ttrue\n"
                    "pref\tstring\tpocket_gb_back_tap\tcontrol\n"
                    "pref\tstring\tpocket_key_back\tk:138\n"
                    "pref\tint\thorizon_control_layout\t0\n", encoding="utf-8")
    adb("push", str(seed), seed_dir + "/seed.txt")
    launch()
    deadline = time.monotonic() + 45
    while True:
        try:
            adb("shell", "test", "-f", seed_dir + "/seed.done")
            break
        except subprocess.SubprocessError:
            if time.monotonic() > deadline:
                raise AssertionError("Seed was not applied")
            time.sleep(0.5)
    time.sleep(2)
    key("BUTTON_B")
    time.sleep(1)
    assert not control(snap()), "B opened Nexus Control"
    # A synthetic Android Back is not a verified dedicated hardware Back.
    launch()
    time.sleep(2)
    tree = open_control()
    for name in ("Sleep", "Wi-Fi", "Bluetooth", "Screenshot", "Do not disturb", "Brightness", "Volume"):
        assert has(tree, name), name + " missing on wide layout"
    wait_for("initial Sleep focus", lambda t: control(t) and focused(t, "Sleep"))
    capture("01-wide")
    key("DPAD_RIGHT")
    wait_for("Wi-Fi focus", lambda t: focused(t, "Wi-Fi"))
    key("DPAD_RIGHT")
    wait_for("Bluetooth focus", lambda t: focused(t, "Bluetooth"))
    key("DPAD_RIGHT")
    wait_for("brightness focus", lambda t: focused(t, "Brightness"))
    key("DPAD_DOWN")
    wait_for("volume focus", lambda t: focused(t, "Volume"))
    key("DPAD_DOWN")
    wait_for("escape from slider to action", lambda t: focused(t, "Settings") or focused(t, "Performance"))
    capture("02-wide-focus")
    key("BUTTON_B")
    wait_for("B closes Control", lambda t: not control(t))
    print("PASS: wide workspace actions, controller slider entry and exit, B closure", flush=True)

    setup_device(480)
    open_control()
    capture("03-compact")
    wait_for("compact initial focus", lambda t: focused(t, "Sleep"))
    for _ in range(8):
        tree = snap()
        if focused(tree, "Volume"):
            break
        key("DPAD_DOWN")
        time.sleep(0.3)
    wait_for("scroll to compact volume", lambda t: focused(t, "Volume"))
    capture("04-compact-scrolled")
    key("BACK")
    wait_for("Back closes compact Control", lambda t: not control(t))
    print("PASS: compact scroll and controller access", flush=True)

    setup_device(320)
    key("BUTTON_START")
    quick_settings = wait_for("console settings", lambda t: has(t, "Settings") and has(t, "System"))
    tap_label(quick_settings, "Streaming")
    cap = "Battery Saver brightness cap"
    streaming = wait_for("stream brightness scene setting", lambda t: has(t, cap))
    tap_label(streaming, cap)
    streaming = wait_for("owner can set a scoped brightness limit", lambda t: has(t, cap) and has(t, "30%"))
    prefs = adb("shell", "run-as", PKG, "cat", "shared_prefs/" + PKG + "_preferences.xml").decode("utf-8")
    assert '<int name="horizon_scene_display_cap" value="30"' in prefs, "display scene choice was not saved"
    capture("07-stream-display-scene")
    tap_label(streaming, "Custom stream equalizer")
    wait_for("custom EQ and explicit step controls", lambda t: has(t, "Decrease Bass · 120 Hz"))
    key("DPAD_DOWN")
    key("DPAD_LEFT")
    eq = wait_for("controller reduces the first band", lambda t: has(t, "-1 dB"))
    tap_label(eq, "Decrease Bass · 120 Hz")
    eq = wait_for("touch reduces the same band", lambda t: has(t, "-2 dB"))
    tap_label(eq, "Increase Bass · 120 Hz")
    eq = wait_for("touch can also increase the band", lambda t: has(t, "-1 dB"))
    capture("10-stream-equalizer")
    tap_label(eq, "Use custom EQ on next stream")
    streaming = wait_for("EQ editor returns to Streaming settings", lambda t: has(t, cap) and not has(t, "Bass · 120 Hz"))
    prefs = adb("shell", "run-as", PKG, "cat", "shared_prefs/" + PKG + "_preferences.xml").decode("utf-8")
    assert '<int name="horizon_eq_bass" value="-1"' in prefs
    assert '<string name="horizon_stream_dsp">custom</string>' in prefs
    tap_label(streaming, "Libraries")
    library_settings = wait_for("RomM catalog entry in Library settings", lambda t: has(t, "RomM catalog"))
    tap_label(library_settings, "RomM catalog")
    wait_for("RomM requests the user's HTTPS address before starting a connection", lambda t: has(t, "RomM server"))
    capture("08-romm-setup")
    key("BUTTON_B")
    library_settings = wait_for("RomM setup cancels back to Library settings", lambda t:
                                has(t, "RomM catalog") and not has(t, "RomM server"))
    tap_label(library_settings, "System")
    recovery = wait_for("Android launcher recovery action", lambda t: has(t, "Recovery")
                        and has(t, "Open another Android launcher") and has(t, "Compatibility stream start"))
    tap_label(recovery, "Compatibility stream start")
    armed = wait_for("compatibility mode arms for the next manual stream",
                     lambda t: has(t, "Next manual stream"))
    tap_label(armed, "Compatibility stream start")
    recovery = wait_for("compatibility mode can be cancelled",
                        lambda t: has(t, "Compatibility stream start") and not has(t, "Next manual stream"))
    capture("05-system-recovery")
    tap_label(recovery, "Open another Android launcher")
    wait_for("Android launcher or launcher chooser", lambda t: not has(t, "Open another Android launcher")
             and any(n.get("package") not in ("", None, PKG) for n in t.iter("node")))
    capture("06-android-launcher")
    print("PASS: System offers an Android launcher escape", flush=True)

    setup_device(320)
    key("BUTTON_START")
    settings = wait_for("settings after launcher recovery", lambda t: has(t, "Settings") and has(t, "System"))
    tap_label(settings, "System")
    device = wait_for("System settings", lambda t: has(t, "Compatibility stream start"))
    # Tapping System focuses its first row. D-pad focus scrolls the actual Settings
    # container to each next row, independent of display size and touch-scroll inertia.
    for _ in range(50):
        if focused(device, "Retroid Bridge"):
            break
        key("DPAD_DOWN")
        time.sleep(0.2)
        device = snap()
    assert focused(device, "Retroid Bridge"), "Hardware diagnostics could not be reached by controller"
    tap_label(device, "Retroid Bridge")
    bridge = wait_for("sampled hardware observations", lambda t: has(t, "Sampled at") and has(t, "Refresh readings"))
    capture("09-hardware-readings")
    tap_label(bridge, "Refresh readings")
    wait_for("refreshed hardware observations", lambda t: has(t, "Sampled at") and has(t, "Refresh readings"))
    key("BUTTON_B")
    wait_for("return to System from hardware diagnostics", lambda t: has(t, "System") and not has(t, "Sampled at"))
    print("PASS: Retroid Bridge opens, refreshes, and returns to System", flush=True)
    tap_label(wait_for("Bridge entry after dismissal", lambda t: has(t, "Retroid Bridge")), "Retroid Bridge")
    bridge = wait_for("hardware observations before the preview", lambda t: has(t, "Sampled at"))
    for _ in range(8):
        action = next((n for n in bridge.iter("node") if n.get("text") == "CPU profile preview"), None)
        if action is not None:
            edges = list(map(int, re.findall(r"\d+", action.get("bounds", ""))))
            if len(edges) == 4 and edges[2] > edges[0] and edges[3] > edges[1]:
                break
        # The menu is the last ScrollView, layered over the System Settings scroller.
        scroller = next(n for n in reversed(list(bridge.iter("node")))
                        if n.get("class") == "android.widget.ScrollView")
        left, top, right, bottom = map(int, re.findall(r"\d+", scroller.get("bounds", "")))
        x = str((left + right) // 2)
        adb("shell", "input", "swipe", x, str(bottom - 20), x, str(top + 60), "350")
        time.sleep(0.3)
        bridge = snap()
    tap_label(bridge, "CPU profile preview")
    key("BUTTON_B")
    time.sleep(1)
    wait_for("cancelled CPU preview keeps System visible", lambda t: has(t, "System")
             and not has(t, "CPU profile preview") and not has(t, "Reading device performance…")
             and not any(n.get("text", "").startswith("Read-only calculation from current limits")
                         for n in t.iter("node")))


if __name__ == "__main__":
    try:
        main(sys.argv[1])
    finally:
        RESULTS.mkdir(exist_ok=True)
        for file, args in (("final.png", ("exec-out", "screencap", "-p")),
                           ("crash.log", ("shell", "logcat", "-b", "crash", "-d")),
                           ("settings-focus.log", ("shell", "logcat", "-d", "-s", "NexusSettingsFocus:D", "*:S"))):
            try:
                (RESULTS / file).write_bytes(adb(*args, timeout=8))
            except subprocess.SubprocessError:
                pass
