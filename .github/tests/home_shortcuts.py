"""Drive the installed development APK on a clean English-language CI emulator."""
from pathlib import Path
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET


PACKAGE = "com.pocketnexus.console.debug"
CONTROL = "homeNexusControl"
HEADPHONES = "homeHeadphones"
EMPTY = "No headphones or Bluetooth audio devices detected."
RESULTS = Path("home-ui-results")


def adb(*args, timeout=25):
    return subprocess.check_output(["adb", *args], timeout=timeout)


def launch():
    adb("shell", "am", "start", "-W", "-a", "android.intent.action.MAIN",
        "-c", "android.intent.category.LAUNCHER", "-n", PACKAGE + "/com.limelight.PcView")


def snapshot():
    adb("shell", "uiautomator", "dump", "/sdcard/home-ui.xml")
    xml = adb("exec-out", "cat", "/sdcard/home-ui.xml")
    (RESULTS / "last-screen.xml").write_bytes(xml)
    return ET.fromstring(xml)


def wait_for(description, check):
    deadline = time.monotonic() + 50
    while time.monotonic() < deadline:
        try:
            tree = snapshot()
            if check(tree):
                return tree
        except (ET.ParseError, subprocess.TimeoutExpired, subprocess.CalledProcessError):
            pass
        time.sleep(0.5)
    raise AssertionError("Did not reach " + description)


def shortcut(tree, name):
    return next((n for n in tree.iter("node")
                 if n.get("resource-id", "").endswith(":id/" + name)), None)


def contains(tree, text):
    return any(text.casefold() in (n.get("text", "").casefold(),
                                   n.get("content-desc", "").casefold())
               for n in tree.iter("node"))


def home(tree):
    return (shortcut(tree, CONTROL) is not None and shortcut(tree, HEADPHONES) is not None
            and not contains(tree, EMPTY) and not contains(tree, "Screenshot"))


def capture(label):
    (RESULTS / (label + ".png")).write_bytes(adb("exec-out", "screencap", "-p"))
    print(label, flush=True)


def key(code):
    adb("shell", "input", "dpad", "keyevent", "KEYCODE_" + code)


def tap(node):
    x1, y1, x2, y2 = map(int, re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]",
                                           node.attrib["bounds"]).groups())
    assert x2 > x1 and y2 > y1, "Shortcut is not on-screen"
    adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))


def verify_destination(name):
    if name == CONTROL:
        wait_for("Nexus Control panel", lambda t: contains(t, "Nexus Control") and
                 contains(t, "Screenshot"))
    else:
        wait_for("Headphones panel and its disconnected state",
                 lambda t: contains(t, "Headphones") and contains(t, EMPTY)
                 and contains(t, "Bluetooth settings"))


def main(apk):
    RESULTS.mkdir(exist_ok=True)
    adb("install", "-g", apk, timeout=180)
    adb("shell", "wm", "size", "1080x1920")
    adb("shell", "wm", "density", "400")
    adb("shell", "input", "keyevent", "KEYCODE_WAKEUP")
    adb("shell", "wm", "dismiss-keyguard")
    launch()

    # The existing emulator preview hook can reproduce a returning user's
    # empty Home without choosing a default launcher or completing pairing.
    seed_dir = "/sdcard/Android/data/" + PACKAGE + "/files/preview"
    adb("shell", "am", "force-stop", PACKAGE)
    adb("shell", "mkdir", "-p", seed_dir)
    seed = RESULTS / "seed.txt"
    seed.write_text("pref\tbool\tpocket_onboarding_pending\tfalse\n"
                    "pref\tbool\tpocket_first_run_done_v1\ttrue\n", encoding="utf-8")
    adb("push", str(seed), seed_dir + "/seed.txt")
    launch()
    tree = wait_for("Home shortcuts", home)
    adb("shell", "test", "-f", seed_dir + "/seed.done")
    capture("01-home")

    for name, label in ((CONTROL, "Nexus Control"), (HEADPHONES, "Headphones")):
        button = shortcut(tree, name)
        assert button.get("clickable") == "true" and button.get("focusable") == "true", name
        assert contains(button, label), name + " has no readable label"
        tap(button)
        verify_destination(name)
        capture("02-control" if name == CONTROL else "03-headphones")
        key("BACK")
        tree = wait_for("Home after Back", home)

    # Start back on the shelf and reach both targets with directional input.
    adb("shell", "am", "force-stop", PACKAGE)
    launch()
    tree = wait_for("Home after relaunch", home)
    for _ in range(6):
        if shortcut(tree, CONTROL).get("focused") == "true":
            break
        key("DPAD_UP")
        tree = wait_for("Home while navigating upward", home)
    assert shortcut(tree, CONTROL).get("focused") == "true", "D-pad could not reach Nexus Control"
    key("DPAD_RIGHT")
    wait_for("Headphones focus", lambda t: home(t) and
             shortcut(t, HEADPHONES).get("focused") == "true")
    key("DPAD_CENTER")
    verify_destination(HEADPHONES)
    key("BACK")
    wait_for("Home after controller navigation", home)
    print("PASS: Home shortcuts, touch, controller and Back", flush=True)


if __name__ == "__main__":
    try:
        main(sys.argv[1])
    except Exception:
        for label, command in (("failure.png", ("exec-out", "screencap", "-p")),
                               ("window.txt", ("shell", "dumpsys", "window", "windows"))):
            try:
                (RESULTS / label).write_bytes(adb(*command, timeout=8))
            except subprocess.SubprocessError:
                pass
        raise
