"""Clients and sources on a clean Android 13 emulator: every entry is labelled as it really is,
missing apps are not presented as available, research and unverified projects make no claims,
an installed upstream client is found with its exact version, App info opens Android's own
screen, and the compact layout with larger text keeps the actions reachable."""
from pathlib import Path
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

PKG = "com.pocketnexus.console.debug"
RESULTS = Path("clients-ui-results")
TITLE = "Clients and sources"


def adb(*args, timeout=25):
    return subprocess.check_output(["adb", *args], timeout=timeout, stderr=subprocess.STDOUT)


def snap():
    adb("shell", "uiautomator", "dump", "/sdcard/clients-ui.xml")
    xml = adb("exec-out", "cat", "/sdcard/clients-ui.xml")
    (RESULTS / "last-screen.xml").write_bytes(xml)
    return ET.fromstring(xml)


def labels(node):
    return node.get("text", ""), node.get("content-desc", "")


def has(tree, label):
    want = label.casefold()
    return any(want in (text.casefold(), desc.casefold()) for text, desc in map(labels, tree.iter("node")))


def contains(tree, fragment):
    want = fragment.casefold()
    return any(want in text.casefold() or want in desc.casefold() for text, desc in map(labels, tree.iter("node")))


def focused(tree, label):
    return any(n.get("focused") == "true" and has(n, label) for n in tree.iter("node"))


def describe(tree):
    if tree is None:
        return "no screen captured"
    seen = []
    for node in tree.iter("node"):
        text, desc = labels(node)
        value = (text or desc).strip().replace("\n", " ")
        if value:
            seen.append(("*" if node.get("focused") == "true" else "") + value[:60])
    return " | ".join(seen[:70])


def wait_for(label, predicate, timeout=60):
    deadline = time.monotonic() + timeout
    tree = None
    while time.monotonic() < deadline:
        try:
            tree = snap()
            if predicate(tree):
                return tree
        except (ET.ParseError, subprocess.SubprocessError):
            pass
        time.sleep(0.5)
    print("Screen: " + describe(tree), flush=True)
    raise AssertionError("Did not reach " + label)


def key(code):
    adb("shell", "input", "dpad", "keyevent", "KEYCODE_" + code)


def capture(label):
    (RESULTS / (label + ".png")).write_bytes(adb("exec-out", "screencap", "-p"))
    print("Captured " + label, flush=True)


def tap_label(tree, label):
    node = next(n for n in tree.iter("node") if label in (n.get("text"), n.get("content-desc")))
    left, top, right, bottom = map(int, re.findall(r"\d+", node.get("bounds", "")))
    assert left < right and top < bottom
    adb("shell", "input", "tap", str((left + right) // 2), str((top + bottom) // 2))


def launch():
    adb("shell", "am", "start", "-W", "-a", "android.intent.action.MAIN",
        "-c", "android.intent.category.LAUNCHER", "-n", PKG + "/com.limelight.PcView")


def setup_device(density, font_scale="1.0"):
    adb("shell", "am", "force-stop", PKG)
    adb("shell", "wm", "size", "1080x1920")
    adb("shell", "wm", "density", str(density))
    adb("shell", "settings", "put", "system", "font_scale", font_scale)
    adb("shell", "input", "keyevent", "KEYCODE_WAKEUP")
    adb("shell", "wm", "dismiss-keyguard")
    launch()
    time.sleep(3)


def clients(tree):
    return has(tree, TITLE) and has(tree, "Close") and contains(tree, "Pocket Nexus PC streaming")


def open_clients():
    """Settings > Libraries > Clients and sources, with the controller (the primary path)."""
    settings = None
    for _ in range(4):
        key("BUTTON_START")
        try:
            settings = wait_for("console settings", lambda t: has(t, "Libraries") and not clients(t), timeout=12)
            break
        except AssertionError:
            continue
    assert settings is not None, "Settings did not open from Home"
    move_focus_to("Libraries", "DPAD_DOWN", limit=8)
    wait_for("Library settings", lambda t: has(t, TITLE) and has(t, "RomM catalog"))
    key("DPAD_RIGHT")
    move_focus_to(TITLE, "DPAD_DOWN", limit=12)
    key("BUTTON_A")
    return wait_for("Clients and sources screen", clients)


def move_focus_to(label, direction, limit=50):
    for _ in range(limit):
        tree = snap()
        if focused(tree, label):
            return tree
        key(direction)
        time.sleep(0.25)
    print("Screen: " + describe(snap()), flush=True)
    raise AssertionError("Could not move the focus to " + label)


def main(apk, upstream_apk):
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
                    "pref\tbool\tpocket_first_run_done_v1\ttrue\n", encoding="utf-8")
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

    setup_device(320)
    open_clients()
    wait_for("built-in PC streaming is focused and described as observed", lambda t:
             focused(t, "Pocket Nexus PC streaming")
             and has(t, "Pocket Nexus runs this stream and sees its session.")
             and has(t, "Quick Resume can return to a stream Pocket Nexus started.")
             and has(t, "Pocket Nexus: opens it · starts its games · shows them in the Library · sees the session"
                        " · Quick Resume"))
    wait_for("installed apps are checked, and a missing official app is not offered",
             lambda t: contains(t, "Xbox app, Not installed"))
    capture("01-wide-pc-streaming")

    move_focus_to("Starwire", "DPAD_DOWN")
    wait_for("research projects make no claims", lambda t: has(t, "Research only")
             and contains(t, "Nothing from it is included or available here yet")
             and has(t, "Pocket Nexus: nothing with it yet")
             and has(t, "Instead, when it isn't available: Pocket Nexus PC streaming")
             and has(t, "Open project page") and not has(t, "App info and permissions"))
    capture("02-research-only")

    move_focus_to("Ariane", "DPAD_DOWN")
    wait_for("an unresolved identity has no link or session", lambda t: has(t, "Identity unverified")
             and has(t, "No session runs through Pocket Nexus.") and not has(t, "Open project page"))
    capture("03-unverified")

    move_focus_to("GeForce NOW app", "DPAD_DOWN")
    wait_for("external apps are opened only", lambda t:
             has(t, "Pocket Nexus opens the app and cannot see whether a game is running.")
             and has(t, "Looks for: com.nvidia.geforcenow") and has(t, "Open official page")
             and has(t, "Pocket Nexus: opens it")
             and has(t, "Not installed: Pocket Nexus uses GeForce NOW player")
             and not has(t, "App info and permissions"))
    capture("04-missing-official-app")

    move_focus_to("Xbox Remote Play", "DPAD_DOWN")
    wait_for("official remote play stays with the provider", lambda t: has(t, "Official site")
             and has(t, "The service manages sign-in, queues and play. Pocket Nexus only knows that it opened the service."))
    capture("05-official-web")
    key("BUTTON_B")
    wait_for("B returns to the same Settings row", lambda t: not clients(t) and focused(t, TITLE))
    print("PASS: modes, sessions and missing apps are labelled honestly", flush=True)

    if upstream_apk:
        assert Path(upstream_apk).is_file(), "The pinned upstream APK was not downloaded"
        adb("install", "-g", upstream_apk, timeout=120)
        setup_device(320)
        open_clients()
        move_focus_to("Better xCloud", "DPAD_DOWN")
        wait_for("installed client found with its exact version", lambda t: contains(t, "Installed · 0.24.3")
                 and has(t, "Package: com.redphx.betterxc") and has(t, "App info and permissions")
                 and has(t, "Pocket Nexus: opens it · starts its games")
                 and has(t, "Instead, when it isn't available: Xbox Cloud Gaming player"))
        capture("06-installed-client")
        key("BUTTON_A")
        wait_for("A moves to the first action", lambda t: focused(t, "Open official page"))
        key("DPAD_RIGHT")
        wait_for("App info is reachable", lambda t: focused(t, "App info and permissions"))
        key("BUTTON_A")
        wait_for("Android's own app details", lambda t:
                 any(n.get("package") == "com.android.settings" for n in t.iter("node")))
        capture("07-app-info")
        adb("shell", "input", "keyevent", "KEYCODE_BACK")
        wait_for("back from App info", lambda t: has(t, TITLE) and has(t, "App info and permissions")
                 and contains(t, "Better xCloud"))
        print("PASS: an installed upstream client, its version and App info", flush=True)

    setup_device(480, "1.3")
    open_clients()
    wait_for("compact screen focus", lambda t: focused(t, "Pocket Nexus PC streaming"))
    capture("08-compact-large-text")
    key("DPAD_RIGHT")
    wait_for("actions reachable beside the list", lambda t: focused(t, "Open official page") or focused(t, "Close"))
    capture("09-compact-actions")
    key("BUTTON_B")
    wait_for("compact screen closes", lambda t: not clients(t))
    print("PASS: compact layout with 130% text keeps the list, details and actions", flush=True)


if __name__ == "__main__":
    try:
        main(sys.argv[1], sys.argv[2] if len(sys.argv) > 2 and sys.argv[2] else None)
    finally:
        RESULTS.mkdir(exist_ok=True)
        for file, args in (("final.png", ("exec-out", "screencap", "-p")),
                           ("crash.log", ("shell", "logcat", "-b", "crash", "-d"))):
            try:
                (RESULTS / file).write_bytes(adb(*args, timeout=8))
            except subprocess.SubprocessError:
                pass
        try:
            adb("shell", "settings", "put", "system", "font_scale", "1.0", timeout=8)
        except subprocess.SubprocessError:
            pass
