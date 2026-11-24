"""Exercise existing Back mapping and controller navigation on a clean CI emulator."""
from pathlib import Path
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

def open_control():
    key("BACK")
    return wait_for("Control workspace via existing Back mapping", control)

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
                    "pref\tstring\tpocket_key_back\tauto\n"
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

if __name__ == "__main__":
    try:
        main(sys.argv[1])
    finally:
        RESULTS.mkdir(exist_ok=True)
        for file, args in (("final.png", ("exec-out", "screencap", "-p")),
                           ("crash.log", ("shell", "logcat", "-b", "crash", "-d"))):
            try:
                (RESULTS / file).write_bytes(adb(*args, timeout=8))
            except subprocess.SubprocessError:
                pass
