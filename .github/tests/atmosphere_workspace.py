"""Verify Nexus Atmosphere's layered layout on Android 13: the games on a glass tray, the hero
floating on a glass veil above it, the Library's details and games on glass panes and a lighter
layered Nexus Control, with live blur and with the translucent no-blur fallback, at two sizes.
"""
from pathlib import Path
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

PKG = "com.pocketnexus.console.debug"
RESULTS = Path("atmosphere-ui-results")
ATMOSPHERE_ACCENT = "-1512464"  # 0xFFE8EBF0, the Nexus Atmosphere preset
SEED_DIR = "/sdcard/Android/data/" + PKG + "/files/preview"


def adb(*args, timeout=25):
    return subprocess.check_output(["adb", *args], timeout=timeout, stderr=subprocess.STDOUT)


def snap():
    adb("shell", "uiautomator", "dump", "/sdcard/atmosphere-ui.xml")
    xml = adb("exec-out", "cat", "/sdcard/atmosphere-ui.xml")
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


def dp():
    return int(adb("shell", "wm", "density").decode().split()[-1]) / 160


def atmosphere_home(tree):
    return resource(tree, "homeRow") is not None and resource(tree, "horizon_atmosphere_tray") is not None \
        and resource(tree, "pocketHero") is not None


def seed(lines):
    adb("shell", "am", "force-stop", PKG)
    adb("shell", "mkdir", "-p", SEED_DIR)
    adb("shell", "rm", "-f", SEED_DIR + "/seed.done")
    path = RESULTS / "seed.txt"
    path.write_text("".join(line + "\n" for line in lines), encoding="utf-8")
    adb("push", str(path), SEED_DIR + "/seed.txt")
    launch()
    deadline = time.monotonic() + 45
    while True:
        try:
            adb("shell", "test", "-f", SEED_DIR + "/seed.done")
            return
        except subprocess.SubprocessError:
            if time.monotonic() > deadline:
                raise AssertionError("Seed was not applied")
            time.sleep(0.5)


def check_home(prefix, expect_veil):
    home = wait_for(prefix + " Atmosphere Home", atmosphere_home)
    tray = bounds(resource(home, "horizon_atmosphere_tray"))
    row = bounds(resource(home, "homeRow"))
    hero = bounds(resource(home, "pocketHero"))
    screen = bounds(next(home.iter("node")))
    unit = dp()
    assert tray[2] - tray[0] >= 0.9 * (screen[2] - screen[0]), ("the tray spans the screen", tray, screen)
    assert tray[1] <= row[1] + 1, ("the tray reaches above the games", tray, row)
    assert hero[3] <= tray[1] + 1, ("the hero stands clear of the tray", hero, tray)
    title = resource(home, "pocketHeroTitle")
    if expect_veil:
        inset = bounds(title)[0] - hero[0] if title is not None and title.get("text") else None
        logo = resource(home, "pocketHeroLogo")
        if inset is None and logo is not None:
            inset = bounds(logo)[0] - hero[0]
        assert inset is not None and inset >= 12 * unit - 1, ("the hero sits inside its veil", inset, hero)
        assert tray[1] - hero[3] >= 8 * unit - 1, ("the veil is lifted off the tray", hero, tray)
    capture(prefix + "-home")
    print("PASS: %s Atmosphere Home: tray under the games%s" % (prefix, ", veil around the hero" if expect_veil
                                                                 else ""), flush=True)
    return home


def check_library(prefix):
    home = wait_for(prefix + " Home Library", lambda t: atmosphere_home(t) and has(t, "Library"))
    tile = next(n for n in home.iter("node") if n.get("clickable") == "true"
                and "Library" in (n.get("text"), n.get("content-desc")))
    tap(tile)
    library = wait_for(prefix + " Atmosphere Library", lambda t: resource(t, "horizon_library_grid") is not None
                       and resource(t, "horizon_atmosphere_tray") is not None)
    tray = bounds(resource(library, "horizon_atmosphere_tray"))
    grid = bounds(resource(library, "horizon_library_grid"))
    detail = bounds(resource(library, "horizon_library_detail"))
    assert tray[0] <= grid[0] and tray[1] <= grid[1] and grid[2] <= tray[2] and grid[3] <= tray[3], \
        ("the games sit on their tray", tray, grid)
    apart = detail[2] <= tray[0] or tray[2] <= detail[0] or detail[3] <= tray[1] or tray[3] <= detail[1]
    assert apart, ("the details pane and the games tray are separate layers", detail, tray)
    capture(prefix + "-library")
    key("BUTTON_B")
    wait_for(prefix + " back to Home", atmosphere_home)
    print("PASS: %s Atmosphere Library: details and games on separate glass panes" % prefix, flush=True)


def check_control(prefix):
    key("F8")
    control = wait_for(prefix + " Atmosphere Nexus Control", lambda t: has(t, "Nexus Control")
                       and resource(t, "horizon_atmosphere_panel") is not None)
    wait_for(prefix + " first tile focused", lambda t: focused(t, "Sleep"))
    key("DPAD_RIGHT")
    wait_for(prefix + " controller moves in Atmosphere Control", lambda t: focused(t, "Wi-Fi"))
    capture(prefix + "-control")
    key("BUTTON_B")
    wait_for(prefix + " Control closes", lambda t: not has(t, "Nexus Control"))
    print("PASS: %s Atmosphere Nexus Control pane with controller focus" % prefix, flush=True)


def main(apk):
    RESULTS.mkdir(exist_ok=True)
    adb("install", "-g", apk, timeout=180)
    adb("shell", "svc", "power", "stayon", "true")
    adb("shell", "settings", "put", "system", "screen_off_timeout", "1800000")
    setup_device(320)
    seed(["pref\tbool\tpocket_onboarding_pending\tfalse",
          "pref\tbool\tpocket_first_run_done_v1\ttrue",
          "pref\tbool\tpocket_animations\tfalse",
          "pref\tbool\tpocket_gb_enabled\ttrue",
          "pref\tstring\tpocket_gb_back_tap\tcontrol",
          "pref\tstring\tpocket_key_back\tk:138",
          "pref\tstring\tpocket_home_layout\twide",
          "pref\tint\tpocket_accent\t" + ATMOSPHERE_ACCENT,
          "pref\tstring\tpocket_backdrop\tart",
          "pref\tstring\tpocket_material\tfrost",
          "pref\tstring\tpocket_sound_style\tglass",
          "pref\tstring\tpocket_focus_style\tzoom",
          "pref\tbool\tpocket_live_glass\ttrue",
          "pin\tpc\tAster Circuit\temulator-test-pc\t\t\t501",
          "pin\tpc\tBoreal Drift\temulator-test-pc\t\t\t502",
          "pin\tpc\tCinder Quest\temulator-test-pc\t\t\t503"])

    setup_device(320)
    check_home("01-wide-live", expect_veil=True)
    check_library("01-wide-live")
    check_control("01-wide-live")

    setup_device(480, "1.3")
    check_home("02-compact-130-live", expect_veil=False)
    check_library("02-compact-130-live")
    check_control("02-compact-130-live")

    # The no-blur fallback: the same layers as plain translucent glass
    seed(["pref\tbool\tpocket_live_glass\tfalse"])
    setup_device(320)
    check_home("03-wide-fallback", expect_veil=True)
    check_library("03-wide-fallback")
    check_control("03-wide-fallback")
    print("PASS: Atmosphere layers on Home, Library and Control, live and without blur, at two sizes", flush=True)


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
        try:
            adb("shell", "settings", "put", "system", "font_scale", "1.0", timeout=8)
        except subprocess.SubprocessError:
            pass
