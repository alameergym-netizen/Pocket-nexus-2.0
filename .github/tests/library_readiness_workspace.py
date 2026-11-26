"""Verify S03's install state on Library cards on Android 13: a ROM whose emulator isn't
installed says so on its card, in its details and to a screen reader, while streamed PC games
(nothing to install here) carry no warning.
"""
from pathlib import Path
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

PKG = "com.pocketnexus.console.debug"
RESULTS = Path("library-readiness-ui-results")
FILES = "/sdcard/Android/data/" + PKG + "/files"
ROMS = FILES + "/roms"
RETRO = "Aurora Run"
PC_GAME = "Aster Circuit"


def adb(*args, timeout=25):
    return subprocess.check_output(["adb", *args], timeout=timeout, stderr=subprocess.STDOUT)


def snap():
    adb("shell", "uiautomator", "dump", "/sdcard/readiness-ui.xml")
    xml = adb("exec-out", "cat", "/sdcard/readiness-ui.xml")
    (RESULTS / "last-screen.xml").write_bytes(xml)
    return ET.fromstring(xml)


def labels(node):
    return node.get("text", ""), node.get("content-desc", "")


def has(tree, label):
    want = label.casefold()
    return any(want in (t.casefold(), d.casefold()) for t, d in map(labels, tree.iter("node")))


def resource(tree, name):
    return next((n for n in tree.iter("node") if n.get("resource-id", "").endswith(":id/" + name)), None)


def bounds(node):
    assert node is not None, "expected view missing"
    return tuple(map(int, re.findall(r"-?\d+", node.get("bounds", ""))))


def focused(tree, label):
    return any(n.get("focused") == "true" and has(n, label) for n in tree.iter("node"))


def describe(tree):
    if tree is None:
        return "no screen captured"
    seen = []
    for node in tree.iter("node"):
        text, desc = labels(node)
        rid = node.get("resource-id", "").rsplit("/", 1)[-1]
        value = (text or desc or ("#" + rid if rid else "")).strip().replace("\n", " ")
        if value:
            seen.append(("*" if node.get("focused") == "true" else "") + value[:60])
    return " | ".join(seen[:80])


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


def tap(node):
    left, top, right, bottom = bounds(node)
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


def card(tree, label):
    return next((n for n in tree.iter("node") if n.get("clickable") == "true"
                 and n.get("content-desc") == label), None)


def chip(node):
    return next((n for n in node.iter("node") if n.get("resource-id", "").endswith(":id/horizon_library_needs")),
                None) if node is not None else None


def game_has_focus(tree, label):
    return any(n.get("focused") == "true" and n.get("content-desc") == label for n in tree.iter("node"))


def main(apk):
    RESULTS.mkdir(exist_ok=True)
    adb("install", "-g", apk, timeout=180)
    adb("shell", "svc", "power", "stayon", "true")
    adb("shell", "settings", "put", "system", "screen_off_timeout", "1800000")
    setup_device(320)
    adb("shell", "am", "force-stop", PKG)
    # A ROM in the app's own storage (readable without any permission), for a system whose
    # emulators are not installed on the emulator image
    adb("shell", "mkdir", "-p", ROMS + "/psx", FILES + "/preview")
    adb("shell", "touch", ROMS + "/psx/AuroraRun.cue")
    seed = RESULTS / "seed.txt"
    seed.write_text("pref\tbool\tpocket_onboarding_pending\tfalse\n"
                    "pref\tbool\tpocket_first_run_done_v1\ttrue\n"
                    "pref\tbool\tpocket_animations\tfalse\n"
                    "pref\tstring\tpocket_home_layout\thorizon_workspace\n"
                    "pref\tstring\tpocket_retro_root\t" + ROMS + "\n"
                    "pin\tpc\t" + PC_GAME + "\temulator-test-pc\t\t\t501\n"
                    "pin\tpc\tBoreal Drift\temulator-test-pc\t\t\t502\n"
                    "pin\tretro\t" + RETRO + "\tpsx\tAuroraRun.cue\t" + ROMS + "\t0\n", encoding="utf-8")
    adb("push", str(seed), FILES + "/preview/seed.txt")
    launch()
    deadline = time.monotonic() + 45
    while True:
        try:
            adb("shell", "test", "-f", FILES + "/preview/seed.done")
            break
        except subprocess.SubprocessError:
            if time.monotonic() > deadline:
                raise AssertionError("Seed was not applied")
            time.sleep(0.5)

    setup_device(320)
    home = wait_for("Home with the Library button", lambda t: any(
        n.get("clickable") == "true" and "Library" in (n.get("text"), n.get("content-desc")) for n in t.iter("node")))
    tap(next(n for n in home.iter("node") if n.get("clickable") == "true"
             and "Library" in (n.get("text"), n.get("content-desc"))))
    library = wait_for("Library with the ROM and the PC games", lambda t: resource(t, "horizon_library_grid") is not None
                       and card(t, RETRO) is not None and card(t, PC_GAME) is not None)
    retro_chip = chip(card(library, RETRO))
    assert retro_chip is not None and retro_chip.get("text") in ("Needs app", "No emulator"), \
        ("the ROM's card says it can't start as things are", describe(library))
    assert chip(card(library, PC_GAME)) is None, "a streamed PC game has nothing to install here"
    print("ROM card: " + retro_chip.get("text"), flush=True)
    capture("01-library-cards")

    # The details of the ROM say which app it waits for (controller, so nothing is started)
    key("DPAD_RIGHT")
    tree = snap()
    for _ in range(12):
        if game_has_focus(tree, RETRO):
            break
        key("DPAD_RIGHT")
        time.sleep(0.4)
        tree = snap()
    if not game_has_focus(tree, RETRO):
        for _ in range(6):
            key("DPAD_DOWN")
            time.sleep(0.4)
            tree = snap()
            if game_has_focus(tree, RETRO):
                break
    assert game_has_focus(tree, RETRO), ("the ROM can be focused", describe(tree))
    detail = wait_for("the ROM's details name what it waits for", lambda t: any(
        "(needs " in (n.get("text") or "") or "(no Android emulator)" in (n.get("text") or "")
        for n in resource(t, "horizon_library_detail").iter("node")), timeout=15)
    line = next(n.get("text") for n in resource(detail, "horizon_library_detail").iter("node")
                if "(needs " in (n.get("text") or "") or "(no Android emulator)" in (n.get("text") or ""))
    print("ROM details: " + line, flush=True)
    capture("02-rom-details")
    print("PASS: a ROM without its emulator is marked on its card and in its details; PC games are not",
          flush=True)


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
